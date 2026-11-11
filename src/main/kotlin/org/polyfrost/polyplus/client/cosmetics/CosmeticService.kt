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

    suspend fun equipBothSides(cosmeticId: Int): Result<Unit> {
        val requested = CosmeticCatalog.getCosmeticDefinition(cosmeticId)
            ?: return Result.failure(IllegalArgumentException("Unknown cosmetic id $cosmeticId"))
        if (oppositeSideVariant(requested) == null) return equip(cosmeticId)
        return setEquippedAndSync(bothSides(requested).getOrElse { return Result.failure(it) })
    }

    internal fun bothSides(requested: CosmeticDefinition): Result<Map<BodySlot, Int>> {
        val first = resolveEquip(requested, null).getOrElse { return Result.failure(it) }
        val second = oppositeSideVariant(requested)?.let { resolveEquip(it, null).getOrNull() }
        if (second == null || second.first == first.first) {
            return Result.success(mapOf(first))
        }
        return Result.success(mapOf(first, second))
    }

    private fun oppositeSideVariant(definition: CosmeticDefinition): CosmeticDefinition? {
        val side = definition.preferredSlot()
        val opposite = when (side) {
            BodySlot.LeftHand -> BodySlot.RightHand
            BodySlot.RightHand -> BodySlot.LeftHand
            else -> return null
        }
        if (definition.allowsSlot(opposite)) return null
        val group = CosmeticCatalog.cosmeticGroupViews().firstOrNull { it.groupId == definition.groupId } ?: return null
        val worn = CosmeticCatalog.localEquipped().equipped[opposite]
        if (worn != null && group.variants.none { it.id == worn }) return null
        val candidates = group.variants.filter {
            it.allowsSlot(opposite) && !it.allowsSlot(side) && it.model == definition.model
        }
        val name = withoutSide(definition.variantName)
        return candidates.firstOrNull { withoutSide(it.variantName) == name } ?: candidates.singleOrNull()
    }

    private fun withoutSide(name: String): String =
        name.split(' ')
            .filterNot { it.equals("left", ignoreCase = true) || it.equals("right", ignoreCase = true) }
            .joinToString(" ")

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
