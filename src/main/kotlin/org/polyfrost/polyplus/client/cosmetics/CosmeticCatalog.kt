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

    fun getActiveId(uuid: UUID, type: CosmeticType): Int? =
        BodySlot.defaultFor(type)?.let { remoteEquipped[uuid]?.get(it) }

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

        lock.withLock {
            cosmeticDefinitions.clear()
            for (definition in cosmetics.contents) {
                cosmeticDefinitions[definition.id] = definition
            }
            emoteDefinitions.clear()
            for (definition in emotes?.contents.orEmpty()) {
                emoteDefinitions[definition.id] = definition.asCosmeticDefinition()
            }
        }

        //? if >= 1.21.1 {
        CosmeticAssetCache.preloadDefinitions(cosmetics.contents + emotes?.contents.orEmpty().map { it.asCosmeticDefinition() })
        //?}
        LOGGER.info(
            "Loaded {} cosmetic definition(s) and {} emote definition(s) from API",
            cosmetics.contents.size,
            emotes?.contents?.size ?: 0,
        )
    }

    suspend fun refreshPlayer() {
        val player = PolyPlusClient.HTTP
            .getBodyAuthorized<PlayerCosmetics>("${PolyPlusConfig.apiUrl}/cosmetics/player")
            .onFailure { LOGGER.error("Failed to fetch player cosmetics", it) }
            .getOrNull() ?: return

        lock.withLock {
            localEquipped = EquippedCosmetics(player.equipped)
            ownedCosmeticIds = player.owned.map { it.id }.toSet()
            ownedEmoteIds = player.emotes.map { it.id }.toSet()
            selectedEmoteId?.let {
                if (it !in ownedEmoteIds) {
                    selectedEmoteId = null
                }
            }
            for (definition in player.owned) {
                cosmeticDefinitions[definition.id] = definition
            }
            for (definition in player.emotes) {
                emoteDefinitions[definition.id] = definition.asCosmeticDefinition()
            }
        }

        //? if >= 1.21.1 {
        CosmeticAssetCache.preloadDefinitions(player.owned + player.emotes.map { it.asCosmeticDefinition() })
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
            val slot = BodySlot.defaultFor(definition.type) ?: continue
            map[slot] = id
        }
        applyRemoteEquipped(uuid, map)
    }

    fun applyRemoteEquipped(uuid: UUID, equipment: Map<BodySlot, Int>) {
        if (equipment.isEmpty()) {
            remoteEquipped.remove(uuid)
        } else {
            remoteEquipped[uuid] = equipment
        }
    }

    fun applyRemoteEquippedSlot(uuid: UUID, slot: BodySlot, cosmeticId: Int?) {
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

    fun reset() {
        cosmeticDefinitions.clear()
        emoteDefinitions.clear()
        remoteEquipped.clear()
        localEquipped = EquippedCosmetics()
        selectedEmoteId = null
        ownedCosmeticIds = emptySet()
        ownedEmoteIds = emptySet()
    }
}
