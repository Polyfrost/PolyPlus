package org.polyfrost.polyplus.client.cosmetics

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinModel
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import java.util.function.Supplier
//?} elif > 1.8.9 {
/*import net.minecraft.client.resources.PlayerSkin
*///?} else {
/*import com.mojang.authlib.minecraft.MinecraftProfileTexture
import net.minecraft.client.resource.skin.DefaultSkinUtils
import net.minecraft.resource.Identifier
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
*///?}

object LocalAccountSkin {
    private val LOGGER = LogManager.getLogger("PolyPlus/LocalSkin")
    private val RETRY_MILLIS = longArrayOf(5_000L, 30_000L, 120_000L, 600_000L)
    private const val TEXTURES = "textures"

    private class Resolved(val id: UUID, val profile: GameProfile)

    @Volatile
    private var resolved: Resolved? = null
    @Volatile
    private var bare: GameProfile? = null
    private var pendingId: UUID? = null
    private var fetching = false
    private var attempt = 0
    private var retryAt = 0L

    fun profile(): GameProfile {
        val id = ClientPlatform.localPlayerUuid()
        val name = ClientPlatform.localPlayerName()
        resolved?.let { if (it.id == id) return it.profile }
        launchProfile(id)?.let { if (hasTextures(it)) return remember(id, it) }
        request(id, name)
        return bare?.takeIf { it.id == id && it.name == name } ?: GameProfile(id, name).also { bare = it }
    }

    fun onAccountSwitched(id: UUID, profile: GameProfile?) {
        synchronized(this) {
            pendingId = null
            attempt = 0
            retryAt = 0L
            fetching = false
            resolved = profile?.takeIf { it.id == id && hasTextures(it) }?.let { Resolved(id, it) }
        }
    }

    private fun hasTextures(profile: GameProfile): Boolean = profile.properties.containsKey(TEXTURES)

    private fun remember(id: UUID, profile: GameProfile): GameProfile {
        synchronized(this) {
            resolved?.let { if (it.id == id) return it.profile }
            resolved = Resolved(id, profile)
        }
        return profile
    }

    private fun request(id: UUID, name: String) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (pendingId != id) {
                pendingId = id
                attempt = 0
                retryAt = 0L
                fetching = false
            }
            if (fetching || now < retryAt) return
            fetching = true
        }
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val fetched = runCatching { fetch(id, name) }
                .onFailure { LOGGER.debug("Could not fetch the profile for {}", id, it) }
                .getOrNull()
                ?.takeIf { it.id == id && hasTextures(it) }
            synchronized(this@LocalAccountSkin) {
                if (pendingId != id) return@launch
                fetching = false
                if (fetched != null) {
                    resolved = Resolved(id, fetched)
                } else {
                    retryAt = System.currentTimeMillis() + RETRY_MILLIS[attempt.coerceAtMost(RETRY_MILLIS.lastIndex)]
                    attempt++
                }
            }
        }
    }

    fun knownSlim(): Boolean? {
        val profile = profile()
        if (!hasTextures(profile)) return null
        val texture = runCatching { launchTexture(profile) }.getOrNull() ?: return slim()
        return texture.getMetadata("model") == "slim"
    }

    fun launchSkin(): MojangSkin? = runCatching {
        val texture = launchTexture(profile()) ?: return null
        MojangSkin(texture.url, SkinModel.fromSerialized(texture.getMetadata("model")))
    }.getOrNull()

    //? if > 1.8.9 {
    private fun launchProfile(id: UUID): GameProfile? = runCatching { Minecraft.getInstance().gameProfile }.getOrNull()?.takeIf { it.id == id }

    private fun launchTexture(profile: GameProfile) = Minecraft.getInstance()
        //? if >= 1.21.10 {
        .services().sessionService()
        //?} else {
        /*.minecraftSessionService
        *///?}
        .getTextures(profile).skin()

    private fun fetch(id: UUID, name: String): GameProfile? = Minecraft.getInstance()
        //? if >= 1.21.10 {
        .services().sessionService()
        //?} else {
        /*.minecraftSessionService
        *///?}
        .fetchProfile(id, true)?.profile()
    //?} else {
    /*private fun launchProfile(id: UUID): GameProfile? {
        val properties = runCatching { (Minecraft.getInstance() as MinecraftAccessor).`polyplus$getProfileProperties`() }.getOrNull() ?: return null
        val copy = runCatching { properties.get(TEXTURES).toList() }.getOrNull().orEmpty()
        if (copy.isEmpty()) return null
        return GameProfile(id, ClientPlatform.localPlayerName()).also { profile -> copy.forEach { profile.properties.put(TEXTURES, it) } }
    }

    private fun fetch(id: UUID, name: String): GameProfile? =
        Minecraft.getInstance().sessionService.fillProfileProperties(GameProfile(id, name), true)

    private fun launchTexture(profile: GameProfile) =
        Minecraft.getInstance().sessionService.getTextures(profile, false)[MinecraftProfileTexture.Type.SKIN]
    *///?}

    //? if >= 1.21.10 {
    private var lookup: Pair<GameProfile, Supplier<PlayerSkin>>? = null

    fun skin(): PlayerSkin {
        val profile = profile()
        val current = lookup?.takeIf { it.first === profile }
            ?: (profile to Minecraft.getInstance().skinManager.createLookup(profile, false)).also { lookup = it }
        return current.second.get()
    }

    fun slim(): Boolean = skin().model() == PlayerModelType.SLIM
    //?} elif > 1.8.9 {
    /*fun skin(): PlayerSkin = Minecraft.getInstance().skinManager.getInsecureSkin(profile())

    fun slim(): Boolean = skin().model() == PlayerSkin.Model.SLIM
    *///?} else {
    /*class Textures {
        @Volatile
        var texture: Identifier? = null
        @Volatile
        var slim: Boolean? = null
        @Volatile
        var cape: Identifier? = null
    }

    private var loaded: Pair<GameProfile, Textures>? = null

    fun textures(): Textures? {
        val profile = profile()
        if (!hasTextures(profile)) return null
        loaded?.let { if (it.first === profile) return it.second }
        val textures = Textures()
        loaded = profile to textures
        runCatching {
            Minecraft.getInstance().skinManager.register(profile, { type, location, texture ->
                when (type) {
                    MinecraftProfileTexture.Type.SKIN -> {
                        textures.slim = texture.getMetadata("model") == "slim"
                        textures.texture = location
                    }
                    MinecraftProfileTexture.Type.CAPE -> textures.cape = location
                    else -> {}
                }
            }, false)
        }
        return textures
    }

    fun slim(): Boolean = textures()?.slim ?: (DefaultSkinUtils.getDefaultModelType(ClientPlatform.localPlayerUuid()) == "slim")
    *///?}
}
