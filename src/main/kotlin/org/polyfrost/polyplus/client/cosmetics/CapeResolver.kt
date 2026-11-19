package org.polyfrost.polyplus.client.cosmetics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

//? if > 1.8.9 {
import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.gui.preview.PreviewSkin
//?}
//? if >= 1.21.10 {
import net.minecraft.core.ClientAsset
import net.minecraft.world.entity.player.PlayerSkin
//?} elif > 1.8.9 {
/*import net.minecraft.client.resources.PlayerSkin
*///?}

enum class CapeDecision {
    KEEP,
    POLYPLUS,
    MOJANG,
    VANILLA,
    NONE,
}

object CapeResolver {
    private const val VANILLA_NAMESPACE = "minecraft"

    var localOverrideModId by mutableStateOf<String?>(null)
        private set

    // decide() runs for every getSkin call on the render thread; a plain mirror skips the snapshot read there.
    private var lastOverrideModId: String? = null

    val localOverrideModName: String?
        get() = localOverrideModId?.let { id ->
            FabricLoader.getInstance().getModContainer(id).map { it.metadata.name }.orElse(id)
        }

    @JvmStatic
    fun keepsModCape(namespace: String?): Boolean = isModCape(namespace) && !PolyPlusCosmeticsConfig.forcePolyPlusCapes

    private fun isModCape(namespace: String?): Boolean =
        namespace != null && namespace != VANILLA_NAMESPACE && namespace != PolyPlusConstants.ID

    @JvmStatic
    fun decide(playerId: UUID, currentNamespace: String?): CapeDecision =
        resolve(playerId, currentNamespace) { decision, _ -> decision }

    private inline fun <T> resolve(playerId: UUID, currentNamespace: String?, result: (CapeDecision, Identifier?) -> T): T {
        val foreign = isModCape(currentNamespace)
        val local = isLocal(playerId)
        if (local) {
            val modId = if (foreign) currentNamespace else null
            if (lastOverrideModId != modId) {
                lastOverrideModId = modId
                localOverrideModId = modId
            }
        }

        if (foreign && !PolyPlusCosmeticsConfig.forcePolyPlusCapes) return result(CapeDecision.KEEP, null)
        val polyCape = CosmeticAssetCache.getCapeTexture(playerId)
        if (polyCape != null) return result(CapeDecision.POLYPLUS, polyCape)

        val mojangKnown = MojangCapeService.overridesLocalCape(playerId) ||
            (foreign && local && MojangCapeService.isLoaded())
        if (mojangKnown) {
            if (MojangCapeService.localCapeHidden()) return result(CapeDecision.NONE, null)
            if (MojangCapeService.localCapeTexture() != null) return result(CapeDecision.MOJANG, null)
        }
        return result(if (foreign && local) CapeDecision.VANILLA else CapeDecision.KEEP, null)
    }

    //? if > 1.8.9 {
    private object Keep

    @JvmStatic
    fun applyCape(playerId: UUID, profile: GameProfile, original: PlayerSkin): PlayerSkin =
        withCapeChange(original, capeChange(playerId, profile, original))

    internal fun capeChange(playerId: UUID, profile: GameProfile, original: PlayerSkin): Any? =
        resolve(playerId, capeNamespace(original)) { decision, polyCape ->
            when (decision) {
                CapeDecision.POLYPLUS -> polyCape ?: Keep
                CapeDecision.MOJANG -> MojangCapeService.localCapeTexture() ?: Keep
                CapeDecision.VANILLA -> vanillaCape(profile)
                CapeDecision.NONE -> null
                CapeDecision.KEEP -> Keep
            }
        }

    internal fun withCapeChange(original: PlayerSkin, change: Any?): PlayerSkin = when (change) {
        Keep -> original
        is Identifier -> withCape(original, change)
        //? if >= 1.21.10 {
        else -> withCapeFrom(original, change as ClientAsset.Texture?)
        //?} else {
        /*else -> withCapeFrom(original, null)
        *///?}
    }

    private fun vanillaCape(profile: GameProfile) =
        //? if >= 1.21.10 {
        vanillaSkin(profile)?.cape()
        //?} else {
        /*vanillaSkin(profile)?.capeTexture()
        *///?}

    private fun vanillaSkin(profile: GameProfile): PlayerSkin? = runCatching {
        //? if >= 1.21.10 {
        Minecraft.getInstance().skinManager.createLookup(profile, false).get()
        //?} else {
        /*Minecraft.getInstance().skinManager.getInsecureSkin(profile)
        *///?}
    }.getOrNull()

    private fun capeNamespace(skin: PlayerSkin): String? =
        //? if >= 1.21.10 {
        skin.cape()?.texturePath()?.namespace
        //?} else {
        /*skin.capeTexture()?.namespace
        *///?}

    private fun withCape(original: PlayerSkin, cape: Identifier): PlayerSkin =
        //? if >= 1.21.10 {
        withCapeFrom(original, ClientAsset.ResourceTexture(cape))
        //?} else {
        /*withCapeFrom(original, cape)
        *///?}

    //? if >= 1.21.10 {
    private fun withCapeFrom(original: PlayerSkin, cape: ClientAsset.Texture?): PlayerSkin =
        PlayerSkin(original.body(), cape, original.elytra(), original.model(), original.secure())
    //?} else {
    /*private fun withCapeFrom(original: PlayerSkin, cape: Identifier?): PlayerSkin =
        PlayerSkin(original.texture(), original.textureUrl(), cape, original.elytraTexture(), original.model(), original.secure())
    *///?}
    //?}

    private fun isLocal(playerId: UUID): Boolean =
        runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() == playerId
}

//? if > 1.8.9 {
class PlayerSkinCache {
    private class Entry(val original: PlayerSkin, val cape: Any?, val skin: PreviewSkin?, val result: PlayerSkin)

    private var last: Entry? = null

    fun apply(playerId: UUID, profile: GameProfile, original: PlayerSkin): PlayerSkin {
        val cape = CapeResolver.capeChange(playerId, profile, original)
        val skin = LocalSkinOverride.forPlayer(playerId)?.takeIf { MojangSkinService.isStaleLocalSkin(playerId, bodyId(original)) }
        val hit = last
        if (hit != null && hit.original === original && hit.cape == cape && hit.skin == skin) return hit.result

        val caped = CapeResolver.withCapeChange(original, cape)
        val result = if (skin != null) LocalSkinOverride.withSkin(caped, skin) else caped
        last = Entry(original, cape, skin, result)
        return result
    }

    private fun bodyId(skin: PlayerSkin): String =
        //? if >= 1.21.10 {
        skin.body().texturePath().toString()
        //?} else {
        /*skin.texture().toString()
        *///?}
}
//?}
