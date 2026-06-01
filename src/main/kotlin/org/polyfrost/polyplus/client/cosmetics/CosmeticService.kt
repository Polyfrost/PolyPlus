package org.polyfrost.polyplus.client.cosmetics

import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.http.responses.PartialEquippedCosmetics
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.utils.ClientPlatform

object CosmeticService {
    suspend fun equipCape(cosmeticId: Int): Result<Unit> =
        equip(cosmeticId, BodySlot.Cape)

    suspend fun equipEmote(emoteId: Int): Result<Unit> = runCatching {
        require(emoteId in CosmeticCatalog.ownedEmoteIds()) { "Emote #$emoteId is not in your locker" }
        CosmeticCatalog.setSelectedEmote(emoteId)
    }

    suspend fun clearCape(): Result<Unit> =
        clearSlot(BodySlot.Cape)

    suspend fun clearEmote(): Result<Unit> = runCatching {
        CosmeticCatalog.setSelectedEmote(null)
        CosmeticSync.refreshVisibleSubscriptions().getOrThrow()
        PolyConnection.sendPacket(ServerboundPacket.StopEmote).getOrThrow()
    }

    suspend fun equip(cosmeticId: Int, slot: BodySlot? = null): Result<Unit> {
        val definition = CosmeticCatalog.getCosmeticDefinition(cosmeticId)
            ?: return Result.failure(IllegalArgumentException("Unknown cosmetic id $cosmeticId"))
        if (definition.type == CosmeticType.Emote) {
            return equipEmote(cosmeticId)
        }
        val targetSlot = slot ?: BodySlot.defaultFor(definition.type)
            ?: return Result.failure(IllegalArgumentException("Cosmetic #$cosmeticId cannot be equipped"))
        return setEquippedAndSync(targetSlot, cosmeticId)
    }

    suspend fun clearSlot(slot: BodySlot): Result<Unit> =
        setEquippedAndSync(slot, null)

    fun playEmote(emoteId: Int): Result<Unit> = runCatching {
        require(emoteId in CosmeticCatalog.ownedEmoteIds()) { "Emote #$emoteId is not in your locker" }
        CosmeticCatalog.setSelectedEmote(emoteId)
        CosmeticSync.refreshVisibleSubscriptions().getOrThrow()
        PolyConnection.sendPacket(ServerboundPacket.PlayEmote(emoteId)).getOrThrow()
    }

    fun stopEmote(): Result<Unit> = runCatching {
        CosmeticSync.refreshVisibleSubscriptions().getOrThrow()
        PolyConnection.sendPacket(ServerboundPacket.StopEmote).getOrThrow()
    }

    suspend fun syncLocalActive(): Result<Unit> = runCatching {
        CosmeticCatalog.refreshPlayer()
        val ids = CosmeticCatalog.localEquipped().ids()
        for (id in ids) {
            //? if >= 1.21.1 {
            CosmeticAssetCache.ensureCosmeticLoaded(id)
            //?}
        }
        ClientPlatform.runOnMain {
            CosmeticCatalog.applyRemoteEquipped(ClientPlatform.localPlayerUuid(), CosmeticCatalog.localEquipped().equipped)
            //? if >= 1.21.1 {
            CosmeticSync.applyLocalActiveFromCatalog()
            //?}
        }
    }

    private suspend fun setEquippedAndSync(slot: BodySlot, cosmeticId: Int?): Result<Unit> {
        val setResult = CosmeticCatalog.setEquipped(PartialEquippedCosmetics(mapOf(slot to cosmeticId)))
        if (setResult.isFailure) {
            return setResult
        }
        val realtimeResult = PolyConnection.sendPacket(ServerboundPacket.SetEquippedCosmetic(slot, cosmeticId))
        if (realtimeResult.isFailure) {
            return realtimeResult
        }
        return syncLocalActive()
    }
}
