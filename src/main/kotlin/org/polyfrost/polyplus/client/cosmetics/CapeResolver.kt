package org.polyfrost.polyplus.client.cosmetics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

//? if > 1.8.9 {
import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
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

    val localOverrideModName: String?
        get() = localOverrideModId?.let { id ->
            FabricLoader.getInstance().getModContainer(id).map { it.metadata.name }.orElse(id)
        }

    @JvmStatic
    fun decide(playerId: UUID, currentNamespace: String?): CapeDecision {
        val foreign = currentNamespace != null &&
            currentNamespace != VANILLA_NAMESPACE &&
            currentNamespace != PolyPlusConstants.ID
        val local = isLocal(playerId)
        if (local) {
            val modId = if (foreign) currentNamespace else null
            if (localOverrideModId != modId) localOverrideModId = modId
        }

        if (foreign && !PolyPlusCosmeticsConfig.forcePolyPlusCapes) return CapeDecision.KEEP
        if (CosmeticAssetCache.getCapeTexture(playerId) != null) return CapeDecision.POLYPLUS

        val mojangKnown = MojangCapeService.overridesLocalCape(playerId) ||
            (foreign && local && MojangCapeService.isLoaded())
        if (mojangKnown) {
            if (MojangCapeService.localCapeHidden()) return CapeDecision.NONE
            if (MojangCapeService.localCapeTexture() != null) return CapeDecision.MOJANG
        }
        return if (foreign && local) CapeDecision.VANILLA else CapeDecision.KEEP
    }

    //? if > 1.8.9 {
    @JvmStatic
    fun applyCape(playerId: UUID, profile: GameProfile, original: PlayerSkin): PlayerSkin =
        when (decide(playerId, capeNamespace(original))) {
            CapeDecision.POLYPLUS -> withCape(original, CosmeticAssetCache.getCapeTexture(playerId))
            CapeDecision.MOJANG -> withCape(original, MojangCapeService.localCapeTexture())
            CapeDecision.VANILLA -> withCapeFrom(original, vanillaSkin(profile))
            CapeDecision.NONE -> withCapeFrom(original, null)
            CapeDecision.KEEP -> original
        }

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

    private fun withCape(original: PlayerSkin, cape: Identifier?): PlayerSkin {
        if (cape == null) return original
        //? if >= 1.21.10 {
        return PlayerSkin(original.body(), ClientAsset.ResourceTexture(cape), original.elytra(), original.model(), original.secure())
        //?} else {
        /*return PlayerSkin(original.texture(), original.textureUrl(), cape, original.elytraTexture(), original.model(), original.secure())
        *///?}
    }

    private fun withCapeFrom(original: PlayerSkin, source: PlayerSkin?): PlayerSkin =
        //? if >= 1.21.10 {
        PlayerSkin(original.body(), source?.cape(), original.elytra(), original.model(), original.secure())
        //?} else {
        /*PlayerSkin(original.texture(), original.textureUrl(), source?.capeTexture(), original.elytraTexture(), original.model(), original.secure())
        *///?}
    //?}

    private fun isLocal(playerId: UUID): Boolean =
        runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() == playerId
}
