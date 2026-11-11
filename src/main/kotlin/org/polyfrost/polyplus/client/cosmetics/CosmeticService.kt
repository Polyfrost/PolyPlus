package org.polyfrost.polyplus.client.cosmetics

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitLibrary
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.http.responses.PartialEquippedCosmetics
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching

object CosmeticService {
    private val LOGGER = LogManager.getLogger("polyplus/cosmetics")
    suspend fun equipEmote(emoteId: Int): Result<Unit> = runCatching {
        require(emoteId in CosmeticCatalog.ownedEmoteIds()) { "Emote #$emoteId is not in your locker" }
        CosmeticCatalog.setSelectedEmote(emoteId)
    }

    suspend fun setParticleColor(color: Int?): Result<Unit> = runCatching {
        val uuid = ClientPlatform.localPlayerUuid()
        ClientPlatform.runOnMain {
            if (color != null) {
                CosmeticCatalog.setParticleColor(uuid, color)
            } else {
                CosmeticCatalog.clearParticleColor(uuid)
            }
        }
        PolyConnection.sendPacket(ServerboundPacket.SetParticleColor(color)).getOrThrow()
        OutfitLibrary.onParticleColorChanged(color)
    }

    suspend fun clearEmote(): Result<Unit> = runCatching {
        CosmeticCatalog.setSelectedEmote(null)
        refreshSubscriptionsBestEffort()
        PolyConnection.sendPacket(ServerboundPacket.StopEmote).getOrThrow()
    }

    suspend fun equip(cosmeticId: Int, slot: BodySlot? = null): Result<Unit> {
        val requested = CosmeticCatalog.getCosmeticDefinition(cosmeticId)
            ?: return Result.failure(IllegalArgumentException("Unknown cosmetic id $cosmeticId"))
        if (requested.type == CosmeticType.Emote) {
            return equipEmote(cosmeticId)
        }
        val (targetSlot, resolvedId) = resolveEquip(requested, slot).getOrElse { return Result.failure(it) }
        return setEquippedAndSync(mapOf(targetSlot to resolvedId))
    }

    suspend fun equipPieces(cosmeticIds: List<Int>, clear: Collection<BodySlot> = emptyList()): Result<Unit> {
        val changes = LinkedHashMap<BodySlot, Int?>()
        for (slot in clear) changes[slot] = null
        for (cosmeticId in cosmeticIds) {
            val requested = CosmeticCatalog.getCosmeticDefinition(cosmeticId)
                ?: return Result.failure(IllegalArgumentException("Unknown cosmetic id $cosmeticId"))
            val (slot, resolvedId) = resolveEquip(requested, null).getOrElse { return Result.failure(it) }
            changes[slot] = resolvedId
        }
        return setEquippedAndSync(changes)
    }

    internal fun equipTarget(requested: CosmeticDefinition): Result<Pair<BodySlot, Int>> = resolveEquip(requested, null)

    private fun resolveEquip(requested: CosmeticDefinition, slot: BodySlot?): Result<Pair<BodySlot, Int>> {
        val cosmeticId = requested.id
        // slim/wide comes from the local player's skin model not from the user
        val resolvedId = if (requested.model != null) {
            val slim = ClientPlatform.runOnMainSync { ClientPlatform.localSkinSlim() }
            CosmeticCatalog.resolveVariantForSkin(cosmeticId, slim)
        } else {
            cosmeticId
        }
        val definition = CosmeticCatalog.getCosmeticDefinition(resolvedId) ?: requested
        val targetSlot = slot ?: definition.preferredSlot()
            ?: return Result.failure(IllegalArgumentException("Cosmetic #$cosmeticId cannot be equipped"))
        if (!BodySlot.isEquippableSlot(targetSlot)) {
            return Result.failure(IllegalArgumentException("Cosmetic #$resolvedId cannot be equipped"))
        }
        if (!definition.allowsSlot(targetSlot)) {
            return Result.failure(
                IllegalArgumentException("Cosmetic #$resolvedId cannot be equipped in slot ${targetSlot.serializedName}"),
            )
        }
        return Result.success(targetSlot to resolvedId)
    }

    suspend fun clearSlot(slot: BodySlot): Result<Unit> =
        setEquippedAndSync(mapOf(slot to null))

    suspend fun clearSlots(slots: Collection<BodySlot>): Result<Unit> =
        if (slots.isEmpty()) Result.success(Unit) else setEquippedAndSync(slots.associateWith { null })

    fun playEmote(emoteId: Int): Result<Unit> = runCatching {
        require(emoteId in CosmeticCatalog.ownedEmoteIds()) { "Emote #$emoteId is not in your locker" }
        CosmeticCatalog.setSelectedEmote(emoteId)
        refreshSubscriptionsBestEffort()
        PolyConnection.sendPacket(ServerboundPacket.PlayEmote(emoteId)).getOrThrow()
    }

    fun stopEmote(): Result<Unit> = runCatching {
        refreshSubscriptionsBestEffort()
        PolyConnection.sendPacket(ServerboundPacket.StopEmote).getOrThrow()
    }

    private fun refreshSubscriptionsBestEffort() {
        CosmeticSync.refreshVisibleSubscriptions().onFailure {
            LOGGER.warn("Failed to refresh visible player subscriptions", it)
        }
    }

    suspend fun syncLocalActive(): Result<Unit> = runSuspendCatching {
        CosmeticCatalog.refreshPlayer()
        val ids = CosmeticCatalog.localEquipped().ids()
        for (id in ids) {
            //? if >= 1.21.1 || = 1.8.9 {
            CosmeticAssetCache.ensureCosmeticLoaded(id)
            //?}
        }
        ClientPlatform.runOnMain {
            CosmeticCatalog.applyRemoteEquipped(ClientPlatform.localPlayerUuid(), CosmeticCatalog.localEquipped().equipped)
            //? if >= 1.21.1 || = 1.8.9 {
            CosmeticSync.applyLocalActiveFromCatalog()
            //?}
        }
    }

    private suspend fun setEquippedAndSync(changes: Map<BodySlot, Int?>): Result<Unit> {
        val setResult = CosmeticCatalog.setEquipped(PartialEquippedCosmetics(changes))
        if (setResult.isFailure) {
            return setResult
        }
        for ((slot, cosmeticId) in changes) {
            val realtimeResult = PolyConnection.sendPacket(ServerboundPacket.SetEquippedCosmetic(slot, cosmeticId))
            if (realtimeResult.isFailure) {
                return realtimeResult
            }
        }
        return syncLocalActive()
    }
}
