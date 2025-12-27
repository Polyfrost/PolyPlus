package org.polyfrost.polyplus.client

import dev.deftu.omnicore.api.client.commands.OmniClientCommands
import dev.deftu.omnicore.api.client.commands.argument
import dev.deftu.omnicore.api.client.commands.command
import dev.deftu.omnicore.api.client.player.playerUuid
import dev.deftu.omnicore.api.commands.types.enumerable.EnumArgumentType
import dev.deftu.omnicore.api.configDirectory
import dev.deftu.textile.Text
import dev.deftu.textile.minecraft.MCTextStyle
import dev.deftu.textile.minecraft.TextColors
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
import org.polyfrost.polyplus.BackendUrl
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.ApplyCosmetics
import org.polyfrost.polyplus.client.cosmetics.CosmeticManager
import org.polyfrost.polyplus.client.network.http.PolyAuthorization
import org.polyfrost.polyplus.client.network.http.PolyCosmetics
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.utils.EarlyInitializable
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object PolyPlusClient {
    private val LOGGER = LogManager.getLogger(PolyPlusConstants.NAME)
    private var _cachedApiUrl: BackendUrl? = null

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

    @JvmStatic var apiUrl: BackendUrl
        get() {
            if (_cachedApiUrl != null) {
                return _cachedApiUrl!!
            }

            val file = configDirectory.resolve("${PolyPlusConstants.ID}_api_url.txt")
            val url = if (file.exists()) {
                val text = file.readText().trim()
                BackendUrl.entries.find { it.url == text } ?: BackendUrl.PRODUCTION
            } else {
                BackendUrl.PRODUCTION
            }

            _cachedApiUrl = url
            return url
        }
        set(value) {
            _cachedApiUrl = value

            val file = configDirectory.resolve("${PolyPlusConstants.ID}_api_url.txt")
            file.writeText(value.url)
        }

    fun initialize() {
        listOf(
            ApplyCosmetics
        ).forEach(EarlyInitializable::earlyInitialize)

        PolyConnection.initialize {
            LOGGER.info("Connected to PolyPlus WebSocket server.")

            // Request the local player's active cosmetics
            SCOPE.launch {
                PolyConnection.sendPacket(ServerboundPacket.GetActiveCosmetics(playerUuid.toString()))
            }
        }

        refresh()

        OmniClientCommands.command(PolyPlusConstants.ID) {
            then("version") {
                runs { ctx ->
                    val text = Text.literal("PolyPlus Client version: ${PolyPlusConstants.VERSION}")
                        .setStyle(MCTextStyle.color(TextColors.AQUA))
                    ctx.source.replyChat(text)
                }
            }

            then("api") {
                argument("url", EnumArgumentType.of(BackendUrl.entries.toTypedArray())) {
                    runs { ctx ->
                        val url = ctx.argument<BackendUrl>("url")
                        apiUrl = url

                        LOGGER.info("API URL changed to $apiUrl, refreshing API data...")

                        PolyConnection.reconnect() // Reconnect WebSocket under new URL
                        refresh() // Refresh API tokens, cosmetic data, etc.

                        ctx.source.replyChat(Text.literal("API URL changed to ${url.url}, refreshing data...")
                            .setStyle(MCTextStyle.color(TextColors.GREEN)))
                    }
                }
            }
        }.register()
    }

    fun refresh() {
        LOGGER.info("Refreshing PolyPlus Client...")

        // Synchronously (yet asynchronously) refresh all API data in such a way that we authenticate first,
        // then give ourselves time to cache cosmetics, then use said known cached cosmetics to update owned cosmetics.
        SCOPE.launch {
            // Reset authentication
            runCatching { PolyAuthorization.reset() }

            // Reset existing caches
            runCatching { PolyCosmetics.reset() }
            runCatching { CosmeticManager.reset() }

            // Cache all available cosmetics
            runCatching {
                val all = PolyCosmetics.getAll()
                    .await()
                    .getOrNull() ?: return@runCatching
                CosmeticManager.putAll(all.contents)
            }

            // Update the local player's owned cosmetics
            runCatching { PolyCosmetics.updateOwned() }
        }
    }
}
