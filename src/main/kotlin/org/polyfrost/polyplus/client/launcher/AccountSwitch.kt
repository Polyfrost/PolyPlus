package org.polyfrost.polyplus.client.launcher

import com.mojang.authlib.minecraft.UserApiService
import com.mojang.authlib.yggdrasil.ProfileResult
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.client.multiplayer.ProfileKeyPairManager
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
import org.polyfrost.polyplus.mixin.client.access.UserAccessor
import java.util.UUID
import java.util.concurrent.CompletableFuture

object AccountSwitch {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")

    fun apply(account: LauncherAccountStore.StoredAccount): Boolean = runCatching {
        val newId = LauncherAccountStore.parseUuid(account.id) ?: error("bad account id ${account.id}")
        val microsoft = account.kind.equals("microsoft", ignoreCase = true)
        val profile = fetchProfile(newId)
        val userApiService = createUserApiService(account.accessToken, microsoft)
        val userProperties = fetchUserProperties(userApiService)

        val switched = ClientPlatform.runOnMainSync {
            val mc = Minecraft.getInstance()
            val user = mc.user
            val previousId = user.profileId
            mutateUser(user, account.username, newId, account.accessToken)
            //? if < 1.21.10 {
            /*(user as UserAccessor).setType(if (microsoft) User.Type.MSA else User.Type.LEGACY)
            *///?}

            val accessor = mc as MinecraftAccessor
            accessor.setUser(user)
            accessor.setProfileFuture(CompletableFuture.completedFuture(profile))
            accessor.setUserApiService(userApiService)
            userProperties?.let { accessor.setUserPropertiesFuture(CompletableFuture.completedFuture(it)) }
            accessor.setProfileKeyPairManager(keyPairManager(mc, user, userApiService, microsoft))
            previousId != newId
        }
        if (switched) PolyPlusClient.refresh()
        true
    }.getOrElse {
        LOGGER.error("Failed to switch account in-session", it)
        false
    }

    private fun mutateUser(user: User, name: String, uuid: UUID, accessToken: String) {
        val accessor = user as UserAccessor
        accessor.setName(name)
        accessor.setUuid(uuid)
        accessor.setAccessToken(accessToken)
    }

    private fun createUserApiService(accessToken: String, microsoft: Boolean): UserApiService {
        if (!microsoft) return UserApiService.OFFLINE
        return runCatching {
            YggdrasilAuthenticationService(Minecraft.getInstance().proxy).createUserApiService(accessToken)
        }.onFailure {
            LOGGER.warn("Could not create the user API service for the switched account", it)
        }.getOrDefault(UserApiService.OFFLINE)
    }

    private fun fetchUserProperties(service: UserApiService): UserApiService.UserProperties? = runCatching {
        service.fetchProperties()
    }.onFailure {
        LOGGER.warn("Could not fetch the user properties for the switched account", it)
    }.getOrNull()

    private fun keyPairManager(
        mc: Minecraft,
        user: User,
        userApiService: UserApiService,
        microsoft: Boolean,
    ): ProfileKeyPairManager = if (microsoft) {
        ProfileKeyPairManager.create(userApiService, user, mc.gameDirectory.toPath())
    } else {
        ProfileKeyPairManager.EMPTY_KEY_MANAGER
    }

    private fun fetchProfile(id: UUID): ProfileResult? = runCatching {
        Minecraft.getInstance().
            //? if >= 1.21.10 {
            services().sessionService()
            //?} else {
            /*minecraftSessionService
            *///?}
            .fetchProfile(id, true)
    }.onFailure {
        LOGGER.warn("Could not fetch the game profile for {}", id, it)
    }.getOrNull()
}
