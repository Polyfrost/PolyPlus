package org.polyfrost.polyplus.client

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.BackendUrl
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.discord.DiscordPresence
import org.polyfrost.polyplus.client.network.websocket.PolyConnection

object PolyPlusConfig : Config("${PolyPlusConstants.ID}.json", PolyPlusConstants.NAME, Category.OTHER) {
    @JvmStatic
    @Switch(title = "Discord RPC")
    var isDiscordEnabled = true

    @Dropdown(title = "API URL", description = "The URL used for the PolyPlus API. Only change if you know what you're doing.")
    var apiUrl: BackendUrl = BackendUrl.PRODUCTION

    init {
        addCallback("isDiscordEnabled") {
            DiscordPresence.start()
        }

        addCallback("apiUrl") {
            PolyConnection.reconnect()
        }
    }
}
