package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.BackendUrl
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.discord.DiscordPresence
import org.polyfrost.polyplus.client.network.websocket.PolyConnection

object PolyPlusConfig : Config("${PolyPlusConstants.ID}.json", PolyPlusConstants.NAME, Category.OTHER) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Switch(title = "Discord RPC")
    var isDiscordEnabled = true

    @Dropdown(title = "API URL", description = "The URL used for the PolyPlus API. Only change if you know what you're doing.")
    var apiUrl: BackendUrl = BackendUrl.PRODUCTION

    init {
        addCallback("isDiscordEnabled") {
            DiscordPresence.start() // Ensure that Discord RPC is restarted if the setting was toggled
        }

        addCallback("apiUrl") {
            LOGGER.info("API URL changed to $apiUrl, refreshing API data...")
            PolyConnection.reconnect() // Reconnect WebSocket under new URL
            PolyPlusClient.refresh() // Refresh API tokens, cosmetic data, etc.
        }
    }
}
