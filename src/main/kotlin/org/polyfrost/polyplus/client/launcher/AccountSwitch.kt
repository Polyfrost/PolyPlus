package org.polyfrost.polyplus.client.launcher

//? if > 1.8.9 {
import com.mojang.authlib.minecraft.UserApiService
//? if >= 26.3 {
import com.mojang.authlib.services.MinecraftServicesDiscoveryService
import com.mojang.authlib.services.ProfileResult
//?} else {
/*import com.mojang.authlib.yggdrasil.ProfileResult
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService
*///?}
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.client.multiplayer.ProfileKeyPairManager
//?} else {
/*import com.mojang.authlib.GameProfile
import com.mojang.util.UUIDTypeAdapter
import net.minecraft.client.Minecraft
import net.minecraft.client.Session
*///?}
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
//? if > 1.8.9
import org.polyfrost.polyplus.mixin.client.access.UserAccessor
import java.util.UUID
import java.util.concurrent.CompletableFuture

object AccountSwitch {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")

    fun apply(account: LauncherAccountStore.StoredAccount): Boolean = runCatching {
        val newId = LauncherAccountStore.parseUuid(account.id) ?: error("bad account id ${account.id}")
        val microsoft = account.kind.equals("microsoft", ignoreCase = true)
        //? if > 1.8.9 {
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
        //?} else {
        /*val profile = fetchProfile(GameProfile(newId, account.username))
        val switched = ClientPlatform.runOnMainSync {
            val mc = Minecraft.getInstance()
            val previousId = mc.session.profile.id
            val accessor = mc as MinecraftAccessor
            accessor.setSession(
                Session(account.username, UUIDTypeAdapter.fromUUID(newId), account.accessToken, if (microsoft) "mojang" else "legacy"),
            )
            accessor.`polyplus$getProfileProperties`().apply {
                clear()
                profile?.let { putAll(it.properties) }
            }
            previousId != newId
        }
        *///?}
        if (switched) PolyPlusClient.refresh()
        true
    }.getOrElse {
        LOGGER.error("Failed to switch account in-session", it)
        false
    }

    //? if > 1.8.9 {
    private fun mutateUser(user: User, name: String, uuid: UUID, accessToken: String) {
        val accessor = user as UserAccessor
        accessor.setName(name)
        accessor.setUuid(uuid)
        accessor.setAccessToken(accessToken)
    }

    private fun createUserApiService(accessToken: String, microsoft: Boolean): UserApiService {
        if (!microsoft) return UserApiService.OFFLINE
        return runCatching {
            //? if >= 26.3 {
            MinecraftServicesDiscoveryService.create(Minecraft.getInstance().proxy, true).createUserApiService(accessToken)
            //?} else {
            /*YggdrasilAuthenticationService(Minecraft.getInstance().proxy).createUserApiService(accessToken)
            *///?}
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
    //?} else {
    /*private fun fetchProfile(profile: GameProfile): GameProfile? = runCatching {
        Minecraft.getInstance().sessionService.fillProfileProperties(profile, true)
    }.onFailure {
        LOGGER.warn("Could not fetch the game profile for {}", profile.id, it)
    }.getOrNull()
    *///?}
}
