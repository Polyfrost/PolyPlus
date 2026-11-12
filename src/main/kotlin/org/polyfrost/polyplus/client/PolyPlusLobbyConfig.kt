package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.internal.OneConfigConfig
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.lobby.LobbyMusic
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

    @JvmStatic @Include
    var serverListView = false

    @JvmStatic @Include
    var tourSeen = false

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
    @Dropdown(
        title = "Lobby Music",
        description = "Choose the music that plays in the lobby.",
        options = ["Match scene", "Day", "Night", "Sunset", "Aurora", "Home", "Cave", "Nether", "The End", "Minecraft music", "Off"],
    )
    var lobbyMusic: LobbyMusic = LobbyMusic.MATCH_SCENE

    @JvmStatic
    @Slider(
        title = "Lobby Music Volume",
        description = "How loud the lobby music plays, in percent.",
        min = 0f,
        max = 100f,
        step = 5f,
    )
    var lobbyMusicVolume = 100f

    @JvmStatic @Include
    var lobbyMusicVolumeInherited = false

    @JvmStatic
    @Switch(
        title = "Mute When Unfocused",
        description = "Fade out the lobby music while the game window isn't focused.",
    )
    var muteLobbyMusicUnfocused = false

    @JvmStatic
    @Switch(
        title = "Quit Without Confirmation",
        description = "Close the game as soon as you press Quit in the lobby, without asking first.",
    )
    var quitWithoutConfirmation = false

    init {
        preload()
        migrateFromSplitConfigs()
        hideIf("lobbyMusicVolume") { lobbyMusic == LobbyMusic.OFF || lobbyMusic == LobbyMusic.MINECRAFT }
        hideIf("muteLobbyMusicUnfocused") { lobbyMusic == LobbyMusic.OFF || lobbyMusic == LobbyMusic.MINECRAFT }
        inheritMusicVolume()
    }

    fun inheritMusicVolume() {
        if (lobbyMusicVolumeInherited || OneConfigConfig.INSTANCE?.getTree() == null) return
        lobbyMusicVolume = if (OneConfigConfig.enableUIAmbience) OneConfigConfig.uiAmbienceVolume.coerceIn(0f, 100f) else 0f
        lobbyMusicVolumeInherited = true
        save()
    }

    private fun migrateFromSplitConfigs() {
        if (migratedFromSplitConfigs) return
        loadPolyPlusOptions("${PolyPlusConstants.ID}.json", "lobby", LOGGER, ::loadFrom)
        loadPolyPlusOptions("${PolyPlusConstants.ID}-mainmenu.json", "lobby", LOGGER, ::loadFrom)
        migratedFromSplitConfigs = true
        save()
    }
}
