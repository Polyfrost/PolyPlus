package org.polyfrost.polyplus.client.network.http

import dev.deftu.omnicore.api.client.player.playerUuid
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.responses.Cosmetic
import org.polyfrost.polyplus.client.network.http.responses.CosmeticList
import org.polyfrost.polyplus.client.network.http.responses.PlayerCosmetics
import java.util.UUID

object PolyCosmetics {
    private val LOGGER = LogManager.getLogger()
    private var OWNED: List<Cosmetic> = emptyList()
    private val CACHE = HashMap<UUID, HashMap<String, Int>>()

    suspend fun updateOwned() {
        val playerCosmetics = PolyPlusClient.HTTP
            .getBodyAuthorized<PlayerCosmetics>("${PolyPlusClient.apiUrl}/cosmetics/player")
            .onFailure { LOGGER.error("Failed to fetch owned cosmetics", it) }
            .getOrElse { return LOGGER.warn("Could not fetch owned cosmetics for player $playerUuid") }
        OWNED = playerCosmetics.owned
    }

    fun getAll(): Deferred<Result<CosmeticList>> = PolyPlusClient.SCOPE.async {
        runCatching {
            PolyPlusClient.HTTP
                .get("${PolyPlusClient.apiUrl}/cosmetics")
                .body<CosmeticList>()
        }.onFailure {
            LOGGER.warn("Failed to fetch all cosmetics: ${it.message}")
        }
    }

    fun reset() {
        LOGGER.info("Resetting cosmetics cache: Size before reset: ${CACHE.size}")
        CACHE.clear()
    }

    fun getFor(uuid: UUID): HashMap<String, Int>? {
        return CACHE[uuid]
    }

    fun cacheActive(uuid: UUID, type: String, id: Int) {
        CACHE.getOrPut(uuid) { HashMap() }[type] = id
    }

    fun removeFromCache(uuid: UUID) {
        CACHE.remove(uuid)
    }
}
