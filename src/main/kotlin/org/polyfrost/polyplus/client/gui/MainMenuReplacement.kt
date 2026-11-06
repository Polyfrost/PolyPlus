package org.polyfrost.polyplus.client.gui

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.gui.lobbyshell.onboardingPending
import org.polyfrost.polyplus.client.gui.pausescreen.PauseScreenStyle
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.utils.ClientPlatform

object MainMenuReplacement {
    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")

    private var playing = false
    private var lobbyFailed = false

    @JvmStatic
    fun enabled(): Boolean = !PolyPlusMainMenuConfig.useVanillaMainMenu && !lobbyFailed

    @JvmStatic
    fun isMenu(screen: Screen?): Boolean = screen is PolyPlusLobbyScreen

    @JvmStatic
    fun alreadyOpen(): Boolean = ClientPlatform.currentScreen().let { isMenu(it) || it is PolyPlusOnboardingScreen }

    @JvmStatic
    fun create(): Screen = PolyPlusLobbyScreen()

    @JvmStatic
    fun open() = ClientPlatform.setScreen(create())

    fun dismissVanillaOffer() {
        PolyPlusConfig.vanillaOfferDismissed = true
        PolyPlusConfig.save()
    }

    fun returnToVanilla() {
        PolyPlusMainMenuConfig.useVanillaMainMenu = true
        PolyPlusMainMenuConfig.save()
        PolyPlusConfig.brandedStartup = false
        PolyPlusConfig.pauseScreenStyle = PauseScreenStyle.VANILLA
        dismissVanillaOffer()
        LobbyScene.stop()
        ClientPlatform.setScreen(TitleScreen())
    }

    fun fallBackToVanilla() {
        LOGGER.warn("The lobby can't run, using the vanilla title screen until the next launch")
        lobbyFailed = true
        LobbyScene.stop()
        ClientPlatform.setScreen(
            when {
                ClientPlatform.inWorld() -> null
                onboardingPending() -> PolyPlusOnboardingScreen()
                else -> TitleScreen()
            },
        )
    }

    @JvmStatic
    fun replaceLists(screen: Screen?): Screen? {
        val inWorld = ClientPlatform.inWorld() && !LobbyScene.terrainActive()
        if (inWorld) playing = true
        val left = playing && !inWorld
        if (left && screen is TitleScreen) playing = false
        if (!enabled()) {
            if (inWorld || screen !is PolyPlusLobbyScreen) return screen
            playing = false
            LobbyScene.stop()
            return TitleScreen()
        }
        if (left && screen is PolyPlusLobbyScreen) {
            playing = false
            screen.showPage(LobbyPage.Home)
            return screen
        }
        when (screen?.javaClass) {
            JoinMultiplayerScreen::class.java -> {
                if (!left) return lobbyPage(LobbyPage.Multiplayer)
                playing = false
                return lobbyPage(LobbyPage.Home)
            }
            SelectWorldScreen::class.java -> return lobbyPage(LobbyPage.Singleplayer)
        }
        return screen
    }

    private fun lobbyPage(page: LobbyPage): Screen {
        val open = ClientPlatform.currentScreen() as? PolyPlusLobbyScreen ?: return PolyPlusLobbyScreen(page)
        open.showPage(page)
        return open
    }
}
