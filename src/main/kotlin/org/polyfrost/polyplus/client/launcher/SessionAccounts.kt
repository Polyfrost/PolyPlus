package org.polyfrost.polyplus.client.launcher

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import java.util.UUID

object SessionAccounts {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")

    private var captured = false
    private var launchAccount: LauncherAccountStore.StoredAccount? = null
    private var dismissed = false

    var activeId: String? = null
        private set

    @Synchronized
    fun capture() {
        if (captured) return
        captured = true
        val user = runCatching { Minecraft.getInstance().user }.getOrNull() ?: run {
            captured = false
            return
        }
        val id = runCatching { user.profileId }.getOrNull() ?: return
        val name = runCatching { user.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val token = runCatching { user.accessToken }.getOrNull().orEmpty()

        launchAccount = LauncherAccountStore.StoredAccount(
            id = id.toString(),
            username = name,
            accessToken = token,
            kind = if (id == offlineUuid(name)) "offline" else "microsoft",
        )
        activeId = id.toString()
        LOGGER.info("Captured launch account {} ({})", name, id)
    }

    @Synchronized
    fun transientAccount(store: LauncherAccountStore.CredentialsStore): LauncherAccountStore.StoredAccount? {
        val account = launchAccount?.takeUnless { dismissed } ?: return null
        val id = LauncherAccountStore.parseUuid(account.id) ?: return null
        val persisted = store.users.values.any { LauncherAccountStore.parseUuid(it.id) == id }
        return account.takeUnless { persisted }
    }

    @Synchronized
    fun find(id: UUID): LauncherAccountStore.StoredAccount? =
        launchAccount?.takeUnless { dismissed }?.takeIf { LauncherAccountStore.parseUuid(it.id) == id }

    @Synchronized
    fun dismiss(id: UUID): Boolean {
        val account = launchAccount?.takeUnless { dismissed } ?: return false
        if (LauncherAccountStore.parseUuid(account.id) != id) return false
        dismissed = true
        return true
    }

    @Synchronized
    fun markActive(id: String) {
        activeId = id
    }

    private fun offlineUuid(username: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))
}
