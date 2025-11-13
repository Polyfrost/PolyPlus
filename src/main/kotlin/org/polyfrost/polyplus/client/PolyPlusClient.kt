package org.polyfrost.polyplus.client

import dev.deftu.omnicore.api.client.commands.OmniClientCommands
import dev.deftu.omnicore.api.client.player.playerUuid
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
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.utils.v1.dsl.addDefaultCommand
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.ApplyCosmetics
import org.polyfrost.polyplus.client.cosmetics.CosmeticManager
import org.polyfrost.polyplus.client.discord.DiscordPresence
import org.polyfrost.polyplus.client.gui.FullscreenLockerUI
import org.polyfrost.polyplus.client.network.http.PolyAuthorization
import org.polyfrost.polyplus.client.network.http.PolyCosmetics
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyui.data.PolyImage
import org.polyfrost.polyui.utils.image

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

        listOf(
            ApplyCosmetics
        ).forEach {
            EventManager.INSTANCE.register(it)
        }

        DiscordPresence.initialize()
        PolyConnection.initialize {
            LOGGER.info("Connected to PolyPlus WebSocket server.")

            // Request the local player's active cosmetics
            SCOPE.launch {
                PolyConnection.sendPacket(ServerboundPacket.GetActiveCosmetics(playerUuid.toString()))
            }
        }

        refresh()
        PolyPlusConfig.addDefaultCommand(PolyPlusConstants.ID)
            .then(OmniClientCommands.literal("locker")
                .executes { ctx ->
                    ctx.source.openScreen(FullscreenLockerUI.create())
                })
            .then(OmniClientCommands.literal("refresh")
                .executes { ctx ->
                    refresh()
                    LOGGER.info("PolyPlus Client refresh triggered via command.")
                    val text = Text.literal("PolyPlus will refresh in the background.")
                        .setStyle(MCTextStyle.color(TextColors.GREEN))
                    ctx.source.replyChat(text)
                })
            .then(OmniClientCommands.literal("version")
                .executes { ctx ->
                    val text = Text.literal("PolyPlus Client version: ${PolyPlusConstants.VERSION}")
                        .setStyle(MCTextStyle.color(TextColors.AQUA))
                    ctx.source.replyChat(text)
                })
            .apply(OmniClientCommands::register)
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

    @JvmStatic
    fun getOneClientLogo(): PolyImage {
        val image = "assets/polyplus/brand/oneclient.svg".image()
        return image
    }
}
