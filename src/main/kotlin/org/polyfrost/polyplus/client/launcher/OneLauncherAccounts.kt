package org.polyfrost.polyplus.client.launcher

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import java.util.UUID

object OneLauncherAccounts {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")

    data class Account(
        val id: UUID,
        val username: String,
        val microsoft: Boolean,
        val active: Boolean,
        val expired: Boolean,
    )

    fun list(): List<Account> {
        SessionAccounts.capture()
        val store = LauncherAccountStore.load()
        val session = SessionAccounts.transientAccount(store)
        val activeId = SessionAccounts.activeId?.let { LauncherAccountStore.parseUuid(it) }
            ?: store.defaultUser?.let { LauncherAccountStore.parseUuid(it) }
            ?: store.users.keys.firstNotNullOfOrNull { LauncherAccountStore.parseUuid(it) }

        return (store.users.values + listOfNotNull(session)).mapNotNull { stored ->
            val id = LauncherAccountStore.parseUuid(stored.id) ?: return@mapNotNull null
            val microsoft = LauncherAccountStore.isMicrosoft(stored)
            Account(
                id = id,
                username = stored.username,
                microsoft = microsoft,
                active = id == activeId,
                // The launch account has no refresh token so it can never be refreshed
                expired = microsoft && stored !== session && LauncherAccountStore.isExpired(stored.expires),
            )
        }.sortedWith(compareByDescending<Account> { it.active }.thenBy { it.username.lowercase() })
    }

    fun switchTo(id: UUID): Boolean {
        SessionAccounts.capture()
        val store = LauncherAccountStore.load()
        val stored = store.users[id.toString()]
            ?: store.users.values.firstOrNull { LauncherAccountStore.parseUuid(it.id) == id }
            ?: SessionAccounts.find(id)?.let { session ->
                if (!AccountSwitch.apply(session)) return false
                SessionAccounts.markActive(session.id)
                PolyPlusClient.refresh()
                return true
            }
            ?: run {
                LOGGER.warn("Cannot switch: account {} not in store", id)
                return false
            }
        if (!AccountSwitch.apply(stored)) return false
        SessionAccounts.markActive(stored.id)
        LauncherAccountStore.save(store.copy(defaultUser = stored.id))
        SessionRefresh.refreshAfterSwitch(stored)
        PolyPlusClient.refresh()
        return true
    }

    fun remove(id: UUID): Boolean {
        val store = LauncherAccountStore.load()
        val key = store.users.keys.firstOrNull { LauncherAccountStore.parseUuid(it) == id }
            ?: return SessionAccounts.dismiss(id)
        val users = store.users.toMutableMap().apply { remove(key) }
        val default = if (store.defaultUser == key) users.keys.firstOrNull() else store.defaultUser
        LauncherAccountStore.save(store.copy(users = users, defaultUser = default))
        return true
    }

    suspend fun beginLogin(): MicrosoftAuth.MicrosoftLoginSession = MicrosoftAuth.beginLogin()

    suspend fun finishLogin(session: MicrosoftAuth.MicrosoftLoginSession): Account {
        val account = MicrosoftAuth.finishLogin(session)
        commit(account, makeDefaultIfNone = true)
        return account.toAccount(active = false)
    }

    fun cancelLogin(session: MicrosoftAuth.MicrosoftLoginSession) = MicrosoftAuth.cancelLogin(session)

    suspend fun refresh(id: UUID, refreshClient: Boolean = true): Account {
        val store = LauncherAccountStore.load()
        val key = store.users.keys.firstOrNull { LauncherAccountStore.parseUuid(it) == id }
            ?: error("Account not found")
        val stored = store.users[key] ?: error("Account not found")
        require(LauncherAccountStore.isMicrosoft(stored)) {
            "Only Microsoft accounts can be refreshed"
        }

        val refreshed = MicrosoftAuth.refreshAccount(stored)

        val current = LauncherAccountStore.load()
        val users = current.users.toMutableMap().apply { put(key, refreshed) }
        LauncherAccountStore.save(current.copy(users = users))

        val isDefault = SessionAccounts.activeId?.let { LauncherAccountStore.parseUuid(it) == id }
            ?: (current.defaultUser == key)
        if (isDefault) {
            AccountSwitch.apply(refreshed)
            if (refreshClient) PolyPlusClient.refresh()
        }
        return refreshed.toAccount(active = isDefault)
    }

    fun addOffline(rawUsername: String): Account {
        val username = rawUsername.trim()
        val store = LauncherAccountStore.load()
        val session = SessionAccounts.transientAccount(store)

        require(username.length in 3..16) { "Username must be 3-16 characters" }
        require(username.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }) {
            "Username may only contain letters, digits, and underscores"
        }
        require((store.users.values + listOfNotNull(session)).none { it.username.equals(username, ignoreCase = true) }) {
            "An account named $username already exists"
        }

        val account = LauncherAccountStore.StoredAccount(
            id = offlineUuid(username).toString(),
            username = username,
            expires = java.time.Instant.now().plus(java.time.Duration.ofDays(3650)).toString(),
            kind = "offline",
        )

        commit(account, makeDefaultIfNone = false)
        return account.toAccount(active = false)
    }

    private fun commit(account: LauncherAccountStore.StoredAccount, makeDefaultIfNone: Boolean) {
        val store = LauncherAccountStore.load()
        val users = store.users.toMutableMap().apply { put(account.id, account) }
        val default = if (makeDefaultIfNone && store.defaultUser == null) account.id else store.defaultUser
        LauncherAccountStore.save(store.copy(users = users, defaultUser = default))
    }

    private fun offlineUuid(username: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))

    private fun LauncherAccountStore.StoredAccount.toAccount(active: Boolean) = Account(
        id = LauncherAccountStore.parseUuid(id) ?: UUID(0L, 0L),
        username = username,
        microsoft = LauncherAccountStore.isMicrosoft(this),
        active = active,
        expired = LauncherAccountStore.isMicrosoft(this) && LauncherAccountStore.isExpired(expires),
    )
}
