package org.polyfrost.polyplus.client

import dev.deftu.omnicore.api.client.player.playerUuid
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.events.InitializationEvent
import org.polyfrost.oneconfig.utils.v1.dsl.addDefaultCommand
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticManager
import org.polyfrost.polyplus.client.discord.DiscordPresence
import org.polyfrost.polyplus.client.network.http.PolyCosmetics
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import java.util.function.Consumer

object PolyPlusClient {
    private val LOGGER = LogManager.getLogger(PolyPlusConstants.NAME)

    @JvmField val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @JvmField val JSON = Json {
        prettyPrint = true
        isLenient = true
    }

    @JvmField val HTTP = HttpClient(CIO) {
        defaultRequest {
            userAgent("${PolyPlusConstants.NAME}/${PolyPlusConstants.VERSION}")
        }

        install(ContentNegotiation) {
            json(JSON)
        }

        install(WebSockets)
    }

    fun initialize() {
        PolyPlusConfig.preload()
        DiscordPresence.initialize()
        PolyConnection.initialize()

        SCOPE.launch {
            val all = PolyCosmetics.getAll()
                .await()
                .getOrNull() ?: return@launch
            CosmeticManager.putAll(all.contents)
            PolyCosmetics.updateOwned()
        }

        EventManager.register(InitializationEvent::class.java, Consumer {
            SCOPE.launch {
                PolyConnection.sendPacket(ServerboundPacket.GetActiveCosmetics(playerUuid.toString()))
            }
        })

        PolyPlusConfig.addDefaultCommand(PolyPlusConstants.ID)
    }
}
