package org.polyfrost.polyplus.client.launcher

import org.apache.logging.log4j.LogManager
import java.util.UUID

object OneLauncherAccounts {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")

    data class Account(
        val id: UUID,
        val username: String,
        val microsoft: Boolean,
        val active: Boolean,
    )

    fun list(): List<Account> {
        val store = LauncherAccountStore.load()
        val defaultId = store.defaultUser?.let { LauncherAccountStore.parseUuid(it) }
            ?: store.users.keys.firstNotNullOfOrNull { LauncherAccountStore.parseUuid(it) }

        return store.users.values.mapNotNull { stored ->
            val id = LauncherAccountStore.parseUuid(stored.id) ?: return@mapNotNull null
            Account(
                id = id,
                username = stored.username,
                microsoft = stored.kind.equals("microsoft", ignoreCase = true),
                active = id == defaultId,
            )
        }.sortedWith(compareByDescending<Account> { it.active }.thenBy { it.username.lowercase() })
    }

    fun switchTo(id: UUID): Boolean {
        val store = LauncherAccountStore.load()
        val stored = store.users[id.toString()]
            ?: store.users.values.firstOrNull { LauncherAccountStore.parseUuid(it.id) == id }
            ?: run {
                LOGGER.warn("Cannot switch: account {} not in store", id)
                return false
            }
        if (!AccountSwitch.apply(stored)) return false
        LauncherAccountStore.save(store.copy(defaultUser = stored.id))
        return true
    }

    fun remove(id: UUID): Boolean {
        val store = LauncherAccountStore.load()
        val key = store.users.keys.firstOrNull { LauncherAccountStore.parseUuid(it) == id } ?: return false
        val users = store.users.toMutableMap().apply { remove(key) }
        val default = if (store.defaultUser == key) users.keys.firstOrNull() else store.defaultUser
        LauncherAccountStore.save(store.copy(users = users, defaultUser = default))
        return true
    }

    suspend fun addMicrosoft(): Account {
        val account = MicrosoftAuth.login()
        commit(account, makeDefaultIfNone = true)
        return account.toAccount(active = false)
    }

    fun addOffline(rawUsername: String): Account {
        val username = rawUsername.trim()
        val store = LauncherAccountStore.load()

        require(LauncherAccountStore.hasMicrosoftAccount(store)) {
            "Add a Microsoft account before creating offline accounts"
        }
        require(username.length in 3..16) { "Username must be 3-16 characters" }
        require(username.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }) {
            "Username may only contain letters, digits, and underscores"
        }
        require(store.users.values.none { it.username.equals(username, ignoreCase = true) }) {
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
        microsoft = kind.equals("microsoft", ignoreCase = true),
        active = active,
    )
}
