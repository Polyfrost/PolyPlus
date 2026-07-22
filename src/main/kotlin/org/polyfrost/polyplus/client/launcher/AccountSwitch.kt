package org.polyfrost.polyplus.client.launcher

import net.minecraft.client.Minecraft
import net.minecraft.client.User
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
import org.polyfrost.polyplus.mixin.client.access.UserAccessor

object AccountSwitch {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")

    fun apply(account: LauncherAccountStore.StoredAccount): Boolean = runCatching {
        val newId = LauncherAccountStore.parseUuid(account.id) ?: error("bad account id ${account.id}")
        ClientPlatform.runOnMainSync {
            val mc = Minecraft.getInstance()
            val user = mc.user
            mutateUser(user, account.username, newId, account.accessToken)
            (mc as MinecraftAccessor).setUser(user)
        }
        true
    }.getOrElse {
        LOGGER.error("Failed to switch account in-session", it)
        false
    }

    private fun mutateUser(user: User, name: String, uuid: java.util.UUID, accessToken: String) {
        val accessor = user as UserAccessor
        accessor.setName(name)
        accessor.setUuid(uuid)
        accessor.setAccessToken(accessToken)
    }
}
