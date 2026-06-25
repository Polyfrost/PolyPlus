package org.polyfrost.polyplus.client.cosmetics

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.getBodyAuthorized
import org.polyfrost.polyplus.client.network.http.putAuthorized
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticGroupResponse
import org.polyfrost.polyplus.client.network.http.responses.CosmeticList
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.http.responses.EmoteList
import org.polyfrost.polyplus.client.network.http.responses.EquippedCosmetics
import org.polyfrost.polyplus.client.network.http.responses.PartialEquippedCosmetics
import org.polyfrost.polyplus.client.network.http.responses.PlayerCosmetics
import org.polyfrost.polyplus.client.network.http.responses.SetEquippedCosmeticsRequest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CosmeticCatalog {
    private val LOGGER = LogManager.getLogger()
    private val lock = Mutex()

    private val cosmeticDefinitions = ConcurrentHashMap<Int, CosmeticDefinition>()
    private val emoteDefinitions = ConcurrentHashMap<Int, CosmeticDefinition>()

    private val groupMeta = ConcurrentHashMap<Int, GroupMeta>()
    private val remoteEquipped = ConcurrentHashMap<UUID, Map<BodySlot, Int>>()
    private var localEquipped: EquippedCosmetics = EquippedCosmetics()
    private var selectedEmoteId: Int? = null
    private var ownedCosmeticIds: Set<Int> = emptySet()
    private var ownedEmoteIds: Set<Int> = emptySet()

    fun getDefinition(id: Int): CosmeticDefinition? = cosmeticDefinitions[id] ?: emoteDefinitions[id]

    fun getCosmeticDefinition(id: Int): CosmeticDefinition? = cosmeticDefinitions[id]

    fun getEmoteDefinition(id: Int): CosmeticDefinition? = emoteDefinitions[id]

    fun allDefinitions(): Collection<CosmeticDefinition> = cosmeticDefinitions.values + emoteDefinitions.values

    fun allCosmeticDefinitions(): Collection<CosmeticDefinition> = cosmeticDefinitions.values

    fun allEmoteDefinitions(): Collection<CosmeticDefinition> = emoteDefinitions.values

    fun getRemoteEquipped(uuid: UUID): Map<BodySlot, Int>? = remoteEquipped[uuid]

    fun getActiveId(uuid: UUID, slot: BodySlot): Int? =
        remoteEquipped[uuid]?.get(slot)

    fun getEquippedId(uuid: UUID, slot: BodySlot): Int? =
        remoteEquipped[uuid]?.get(slot)

    fun localEquipped(): EquippedCosmetics = localEquipped

    fun selectedEmoteId(): Int? = selectedEmoteId

    fun setSelectedEmote(id: Int?) {
        selectedEmoteId = id?.takeIf { it in ownedEmoteIds }
    }

    fun ownedIds(): Set<Int> = ownedCosmeticIds + ownedEmoteIds

    fun ownedCosmeticIds(): Set<Int> = ownedCosmeticIds

    fun ownedEmoteIds(): Set<Int> = ownedEmoteIds

    suspend fun refreshCatalog() {
        val cosmetics = runCatching {
            PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/cosmetics").body<CosmeticList>()
        }.onFailure { LOGGER.error("Failed to fetch cosmetic catalog", it) }
            .getOrNull() ?: return

        val emotes = runCatching {
            PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/emotes").body<EmoteList>()
        }.onFailure { LOGGER.error("Failed to fetch emote catalog", it) }
            .getOrNull()

        // Drop groups whose type this client version doesn't recognize
        val knownGroups = cosmetics.contents.filter { it.type != CosmeticType.Unknown }
        val skipped = cosmetics.contents.size - knownGroups.size
        if (skipped > 0) {
            LOGGER.warn("Ignored {} cosmetic group(s) with unknown type/slot", skipped)
        }

        val flattened = knownGroups.flatMap { it.flatten() }

        lock.withLock {
            cosmeticDefinitions.clear()
            for (definition in flattened) {
                cosmeticDefinitions[definition.id] = definition
            }
            groupMeta.clear()
            for (group in knownGroups) {
                groupMeta[group.id] = group.toMeta()
            }
            emoteDefinitions.clear()
            for (definition in emotes?.contents.orEmpty()) {
                emoteDefinitions[definition.id] = definition.asCosmeticDefinition()
            }
        }

        //? if >= 1.21.1 {
        CosmeticAssetCache.preloadDefinitions(flattened + emotes?.contents.orEmpty().map { it.asCosmeticDefinition() })
        //?}
        LOGGER.info(
            "Loaded {} cosmetic group(s) ({} variant(s)) and {} emote definition(s) from API",
            knownGroups.size,
            flattened.size,
            emotes?.contents?.size ?: 0,
        )
    }

    suspend fun refreshPlayer() {
        val player = PolyPlusClient.HTTP
            .getBodyAuthorized<PlayerCosmetics>("${PolyPlusConfig.apiUrl}/cosmetics/player")
            .onFailure { LOGGER.error("Failed to fetch player cosmetics", it) }
            .getOrNull() ?: return

        val ownedGroups = player.owned.filter { it.type != CosmeticType.Unknown }
        val ownedDefs = ownedGroups.flatMap { it.flatten() }
        val equippedKnown = player.equipped.filterKeys { it != BodySlot.Unknown }

        lock.withLock {
            localEquipped = EquippedCosmetics(equippedKnown)
            ownedCosmeticIds = ownedDefs.map { it.id }.toSet()
            ownedEmoteIds = player.emotes.map { it.id }.toSet()
            selectedEmoteId?.let {
                if (it !in ownedEmoteIds) {
                    selectedEmoteId = null
                }
            }
            for (definition in ownedDefs) {
                cosmeticDefinitions[definition.id] = definition
            }
            for (group in ownedGroups) {
                groupMeta[group.id] = group.toMeta()
            }
            for (definition in player.emotes) {
                emoteDefinitions[definition.id] = definition.asCosmeticDefinition()
            }
        }

        //? if >= 1.21.1 {
        CosmeticAssetCache.preloadDefinitions(ownedDefs + player.emotes.map { it.asCosmeticDefinition() })
        //?}
    }

    suspend fun setEquipped(partial: PartialEquippedCosmetics): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.putAuthorized("${PolyPlusConfig.apiUrl}/cosmetics/player") {
            contentType(ContentType.Application.Json)
            setBody(SetEquippedCosmeticsRequest(partial.equipped))
        }
        Unit
    }.onFailure {
        LOGGER.error("Failed to set equipped cosmetics", it)
    }

    fun applyRemoteActive(uuid: UUID, cosmeticIds: List<Int>) {
        val map = mutableMapOf<BodySlot, Int>()
        for (id in cosmeticIds) {
            val definition = getCosmeticDefinition(id) ?: continue
            val slot = definition.preferredSlot() ?: continue
            map[slot] = id
        }
        applyRemoteEquipped(uuid, map)
    }

    fun applyRemoteEquipped(uuid: UUID, equipment: Map<BodySlot, Int>) {
        val known = equipment.filterKeys { it != BodySlot.Unknown }
        if (known.isEmpty()) {
            remoteEquipped.remove(uuid)
        } else {
            remoteEquipped[uuid] = known
        }
    }

    fun applyRemoteEquippedSlot(uuid: UUID, slot: BodySlot, cosmeticId: Int?) {
        if (slot == BodySlot.Unknown) return
        val next = remoteEquipped[uuid].orEmpty().toMutableMap()
        if (cosmeticId == null) {
            next.remove(slot)
        } else {
            next[slot] = cosmeticId
        }
        applyRemoteEquipped(uuid, next)
    }

    fun removeRemote(uuid: UUID) {
        remoteEquipped.remove(uuid)
    }

    /**
     * Cosmetic groups for the picker UI: one entry per buyable cosmetic
     * Emotes are excluded (they have their own list)
     */
    fun cosmeticGroupViews(): List<CosmeticGroupView> =
        groupMeta.values.mapNotNull { meta ->
            val variants = meta.variantIds.mapNotNull { cosmeticDefinitions[it] }
            if (variants.isEmpty()) {
                null
            } else {
                CosmeticGroupView(meta.id, meta.type, meta.name, meta.allowedSlots, variants)
            }
        }

    fun groupContaining(id: Int): CosmeticGroupView? =
        cosmeticGroupViews().firstOrNull { group ->
            group.groupId == id || group.variants.any { it.id == id }
        }

    /**
     * The user-facing variants of a group (label -> representative variant id),
     * in backend order, with the slim/wide model axis collapsed away.
     */
    fun userFacingVariants(group: CosmeticGroupView): List<Pair<String, Int>> {
        val byLabel = LinkedHashMap<String, Int>()
        for (variant in group.variants) {
            byLabel.getOrPut(variant.variantName) { variant.id }
        }
        return byLabel.map { it.key to it.value }
    }

    fun ownedCosmeticGroupIds(): List<Int> =
        cosmeticGroupViews()
            .filter { group -> group.variants.any { it.id in ownedCosmeticIds } }
            .map { it.groupId }

    /**
     * Resolves the variant id to actually equip for a user-facing choice
     */
    fun resolveVariantForSkin(variantId: Int, slim: Boolean): Int {
        val definition = cosmeticDefinitions[variantId] ?: return variantId
        if (definition.model == null) return variantId
        val meta = groupMeta[definition.groupId] ?: return variantId
        val wanted = if (slim) "slim" else "wide"
        val sibling = meta.variantIds
            .asSequence()
            .mapNotNull { cosmeticDefinitions[it] }
            .firstOrNull { it.variantName == definition.variantName && it.model == wanted }
        return sibling?.id ?: variantId
    }

    fun reset() {
        cosmeticDefinitions.clear()
        emoteDefinitions.clear()
        groupMeta.clear()
        remoteEquipped.clear()
        localEquipped = EquippedCosmetics()
        selectedEmoteId = null
        ownedCosmeticIds = emptySet()
        ownedEmoteIds = emptySet()
    }

    private data class GroupMeta(
        val id: Int,
        val type: CosmeticType,
        val name: String,
        val allowedSlots: List<BodySlot>,
        val variantIds: List<Int>,
    )

    private fun CosmeticGroupResponse.toMeta(): GroupMeta =
        GroupMeta(id, type, name, allowedSlots, variants.map { it.id })
}

data class CosmeticGroupView(
    val groupId: Int,
    val type: CosmeticType,
    val name: String,
    val allowedSlots: List<BodySlot>,
    val variants: List<CosmeticDefinition>,
)
