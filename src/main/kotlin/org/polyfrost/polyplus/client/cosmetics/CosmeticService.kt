package org.polyfrost.polyplus.client.cosmetics

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.cosmetics.colors.ColourSaves
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
import org.polyfrost.polyplus.client.cosmetics.colors.LookQueue
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitLibrary
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticEquips
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.emotes.duo.DuoEmotes
import org.polyfrost.polyplus.client.gui.wardrobe.syncWornPalettes
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.network.http.responses.PartialEquippedCosmetics
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.outfits.outfitEquipment
import org.polyfrost.polyplus.client.pets.PetEquips
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import kotlinx.coroutines.withTimeoutOrNull

object CosmeticService {
    private val LOGGER = LogManager.getLogger("polyplus/cosmetics")

    internal val equipQueue = LookQueue()

    internal val skinChanges = SkinChangeGate()

    private val localEquipment by lazy {
        EquipmentSync(
            scope = PolyPlusClient.SCOPE,
            queue = equipQueue,
            refresh = { runSuspendCatching { CosmeticCatalog.refreshPlayer().getOrThrow() } },
            assume = CosmeticCatalog::assumeServerEquipped,
            publish = ::publishLocalEquipment,
            load = ::loadLocalAssets,
        )
    }

    suspend fun equipEmote(emoteId: Int): Result<Unit> = runCatching {
        require(emoteId in CosmeticCatalog.ownedEmoteIds()) { "Emote #$emoteId is not in your locker" }
        CosmeticCatalog.setSelectedEmote(emoteId)
    }

    internal suspend fun setParticleColor(color: Int?, hold: ColourSaves.Hold? = null): Result<Unit> {
        val held = hold ?: ColourSaves.holdAura() ?: return ColourSaves.refused()
        return try {
            saveParticleColor(color)
        } finally {
            held.release()
        }
    }

    private suspend fun saveParticleColor(color: Int?): Result<Unit> = runSuspendCatching {
        val uuid = ClientPlatform.localPlayerUuid()
        ColourSaves.particleChoices.made()
        ClientPlatform.runOnMain { CosmeticCatalog.previewParticleColor(uuid, color) }
        CosmeticColors.saveParticleColor(color).onFailure {
            ClientPlatform.runOnMain {
                ColourSaves.particleChoices.made()
                if (CosmeticCatalog.getParticleColor(uuid) == color) {
                    CosmeticCatalog.previewParticleColor(uuid, CosmeticCatalog.confirmedParticleColor(uuid))
                }
            }
        }.getOrThrow()
        ClientPlatform.runOnMain {
            ColourSaves.particleChoices.made()
            CosmeticCatalog.confirmParticleColor(uuid, color)
        }
        OutfitLibrary.onParticleColorChanged(color)
    }

    fun clearEmote(): Result<Unit> = runCatching {
        ClientPlatform.runOnMainSync {
            DuoEmotes.stopAll()
            CosmeticSync.dropLocalDevEmote()
            CosmeticCatalog.setSelectedEmote(null)
        }
        refreshSubscriptionsBestEffort()
        PolyConnection.sendPacket(ServerboundPacket.StopEmote()).getOrThrow()
    }

    suspend fun equip(cosmeticId: Int, slot: BodySlot? = null, colourTicket: Long = ColourSaves.ticket()): Result<Unit> {
        val requested = CosmeticCatalog.getCosmeticDefinition(cosmeticId)
            ?: return Result.failure(IllegalArgumentException("Unknown cosmetic id $cosmeticId"))
        if (requested.type == CosmeticType.Emote) {
            return equipEmote(cosmeticId)
        }
        if (ColourSaves.blocks(listOf(cosmeticId), colourTicket)) return ColourSaves.refused()
        val slim = skinSlimFor(listOf(requested)) ?: return Result.failure(SkinModelUnknown())
        val (targetSlot, resolvedId) = resolveEquip(requested, slot, slim).getOrElse { return Result.failure(it) }
        return setEquippedAndSync(mapOf(targetSlot to resolvedId), colourTicket)
    }

    suspend fun equipPieces(
        cosmeticIds: List<Int>,
        clear: Collection<BodySlot> = emptyList(),
        colourTicket: Long = ColourSaves.ticket(),
    ): Result<Unit> {
        if (ColourSaves.blocks(cosmeticIds, colourTicket)) return ColourSaves.refused()
        val changes = LinkedHashMap<BodySlot, Int?>()
        for (slot in clear) changes[slot] = null
        val requested = cosmeticIds.map { cosmeticId ->
            CosmeticCatalog.getCosmeticDefinition(cosmeticId)
                ?: return Result.failure(IllegalArgumentException("Unknown cosmetic id $cosmeticId"))
        }
        val slim = skinSlimFor(requested) ?: return Result.failure(SkinModelUnknown())
        for (definition in requested) {
            val (slot, resolvedId) = resolveEquip(definition, null, slim).getOrElse { return Result.failure(it) }
            changes[slot] = resolvedId
        }
        return setEquippedAndSync(changes, colourTicket)
    }

    internal fun equipTarget(requested: CosmeticDefinition, slim: Boolean): Result<Pair<BodySlot, Int>> =
        resolveEquip(requested, null, slim)

    private suspend fun skinSlimFor(requested: List<CosmeticDefinition>): Boolean? {
        if (requested.none { it.model != null }) return false
        withTimeoutOrNull(SKIN_CHANGE_WAIT_MS) { skinChanges.settled() }
        return ClientPlatform.awaitLocalSkinSlim()
    }

    suspend fun awaitSkinChangeFor(cosmeticIds: Collection<Int>) {
        if (cosmeticIds.none { CosmeticCatalog.getCosmeticDefinition(it)?.model != null }) return
        skinChanges.settled()
    }

    private const val SKIN_CHANGE_WAIT_MS = 10_000L

    class SkinModelUnknown : IllegalStateException("Your skin hasn't loaded yet, so the right fit can't be picked")

    private fun resolveEquip(requested: CosmeticDefinition, slot: BodySlot?, slim: Boolean): Result<Pair<BodySlot, Int>> {
        val cosmeticId = requested.id
        // slim/wide comes from the local player's skin model not from the user
        val resolvedId = if (requested.model != null) {
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

    fun playEmote(emoteId: Int, anchor: EmoteAnchor? = null, ticket: Long? = null): Result<Unit> = runCatching {
        require(emoteId in CosmeticCatalog.ownedEmoteIds()) { "Emote #$emoteId is not in your locker" }
        if (!EmoteApi.playable(ticket)) throw EmoteApi.PlayCancelled()
        CosmeticCatalog.setSelectedEmote(emoteId)
        refreshSubscriptionsBestEffort()
        EmoteApi.requestPlay(emoteId, anchor, ticket).getOrThrow()
    }

    fun stopEmote(): Result<Unit> = runCatching {
        refreshSubscriptionsBestEffort()
        PolyConnection.sendPacket(ServerboundPacket.StopEmote()).getOrThrow()
    }

    private fun refreshSubscriptionsBestEffort() {
        CosmeticSync.refreshVisibleSubscriptions().onFailure {
            LOGGER.warn("Failed to refresh visible player subscriptions", it)
        }
    }

    suspend fun syncLocalActive(): Result<Unit> = localEquipment.sync()

    internal suspend fun confirmLocalActive(expected: Map<BodySlot, Int>?): Result<Unit> =
        localEquipment.confirm(expected)

    private fun publishLocalEquipment() {
        ClientPlatform.runOnMain {
            CosmeticCatalog.applyRemoteEquipped(ClientPlatform.localPlayerUuid(), CosmeticCatalog.localEquipped().equipped)
            syncWornPalettes(CosmeticCatalog.localEquipped().ids())
        }
    }

    private suspend fun loadLocalAssets() {
        //? if >= 1.21.1 || = 1.8.9 {
        for (id in CosmeticCatalog.localEquipped().ids()) {
            runSuspendCatching { CosmeticAssetCache.ensureCosmeticLoaded(id) }
                .onFailure { LOGGER.warn("Failed to load equipped cosmetic {}", id, it) }
        }
        ClientPlatform.runOnMain { CosmeticSync.applyLocalActiveFromCatalog() }
        //?}
    }

    fun syncLocalPalettes() {
        ClientPlatform.runOnMain { syncWornPalettes(CosmeticCatalog.localEquipped().ids()) }
    }

    private suspend fun setEquippedAndSync(all: Map<BodySlot, Int?>, colourTicket: Long = Long.MAX_VALUE): Result<Unit> {
        if (BodySlot.Aura in all && ColourSaves.blocksAuraSwap(all[BodySlot.Aura], colourTicket)) return ColourSaves.refused()
        val wearer = ClientPlatform.localPlayerUuid()
        for ((slot, id) in all) CosmeticEquips.changed(wearer, slot, id)
        if (BodySlot.Pet in all) PetEquips.changed(wearer, all[BodySlot.Pet])
        if (BodySlot.Aura in all) CosmeticColors.auraEquipStarted(all[BodySlot.Aura])
        val local = all.filter { (slot, id) -> (id != null && CosmeticCatalog.isLocal(id)) || CosmeticCatalog.hasLocalSlot(slot) }
        if (local.isNotEmpty()) {
            ClientPlatform.runOnMainSync {
                for ((slot, id) in local) CosmeticCatalog.setLocalSlot(slot, id?.takeIf(CosmeticCatalog::isLocal))
            }
        }
        val changes = all.filterValues { it == null || !CosmeticCatalog.isLocal(it) }
            .filterKeys { it !in local || all[it] != null }
        if (changes.isEmpty()) {
            //? if >= 1.21.1 || = 1.8.9 {
            ClientPlatform.runOnMain { CosmeticSync.applyLocalActiveFromCatalog() }
            //?}
            return Result.success(Unit)
        }
        val setResult = CosmeticCatalog.setEquipped(PartialEquippedCosmetics(changes))
        if (setResult.isFailure) {
            return setResult
        }
        OutfitLibrary.onEquipped(changes)
        return confirmLocalActive(outfitEquipment(CosmeticCatalog.serverEquipment(), changes))
    }
}
