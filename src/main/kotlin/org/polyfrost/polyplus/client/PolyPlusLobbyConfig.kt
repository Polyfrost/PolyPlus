package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.lobby.LobbyReveal
import org.polyfrost.polyplus.client.lobby.LobbyTime

object PolyPlusLobbyConfig : Config(
    "${PolyPlusConstants.ID}-lobby.json",
    "Lobby (OneClient)",
    Category.OTHER,
) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Include
    var migratedFromSplitConfigs = false

    @JvmStatic
    @Dropdown(
        title = "Lobby Reveal",
        description = "Choose how the lobby scene appears when the menu opens.",
        options = ["Random", "Wave", "Focus pull", "First light"],
    )
    var lobbyReveal: LobbyReveal = LobbyReveal.RANDOM

    @JvmStatic
    @Dropdown(
        title = "Lobby Time",
        description = "Choose the time of day in the lobby scene.",
        options = ["As captured", "Match theme", "Real time", "Day", "Sunset", "Night"],
    )
    var lobbyTime: LobbyTime = LobbyTime.CAPTURED

    @JvmStatic
    @Switch(
        title = "Quit Without Confirmation",
        description = "Close the game as soon as you press Quit in the lobby, without asking first.",
    )
    var quitWithoutConfirmation = false

    init {
        preload()
        migrateFromSplitConfigs()
    }

    private fun migrateFromSplitConfigs() {
        if (migratedFromSplitConfigs) return
        loadPolyPlusOptions("${PolyPlusConstants.ID}.json", "lobby", LOGGER, ::loadFrom)
        loadPolyPlusOptions("${PolyPlusConstants.ID}-mainmenu.json", "lobby", LOGGER, ::loadFrom)
        migratedFromSplitConfigs = true
        save()
    }
}
