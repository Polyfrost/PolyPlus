package org.polyfrost.polyplus.client.network.http

import dev.deftu.omnicore.api.client.player.playerUuid
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.CosmeticList
import org.polyfrost.polyplus.client.network.http.responses.PlayerCosmetics
import java.util.UUID

object PolyCosmetics {
    private val LOGGER = LogManager.getLogger()
    private val CACHE = HashMap<UUID, HashMap<String, Int>>()

    fun updateOwned() {
        PolyPlusClient.SCOPE.launch {
            val cosmetics = PolyPlusClient.HTTP
                .getBodyAuthorized<PlayerCosmetics>("${PolyPlusConfig.apiUrl}/cosmetics/player")
                .onFailure { LOGGER.error("Failed to fetch owned cosmetics", it) }
                .getOrElse { return@launch LOGGER.warn("Could not fetch owned cosmetics for player $playerUuid") }
                .owned
            for (cosmetic in cosmetics) {
                CACHE[playerUuid] = CACHE.getOrPut(playerUuid) {
                    HashMap()
                }.apply {
                    set(cosmetic.type, cosmetic.id)
                }
            }
        }
    }

    fun getAll(): Deferred<Result<CosmeticList>> = PolyPlusClient.SCOPE.async {
        runCatching {
            PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/cosmetics").body<CosmeticList>()
        }.onFailure {
            LOGGER.warn("Failed to fetch all cosmetics: ${it.message}")
        }
    }

    fun getFor(uuid: UUID): HashMap<String, Int>? {
        return CACHE[uuid]
    }
}
