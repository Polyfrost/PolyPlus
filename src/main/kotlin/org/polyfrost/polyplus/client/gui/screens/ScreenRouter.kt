package org.polyfrost.polyplus.client.gui.screens

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.polyplus.client.PolyPlusScreensConfig
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.DeathScreen
//?} elif = 1.8.9 {
/*import net.minecraft.client.gui.screen.game.DeathScreen
*///?}
import org.polyfrost.polyplus.client.MainMenuChoice
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen
import org.polyfrost.polyplus.client.gui.screens.connection.ConnectionRoutes
import org.polyfrost.polyplus.client.gui.screens.packs.PackRoutes
import org.polyfrost.polyplus.client.gui.screens.realms.RealmsRoutes
import org.polyfrost.polyplus.client.gui.screens.settings.SettingsRoutes
import org.polyfrost.polyplus.client.gui.screens.world.WorldRoutes
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.utils.ClientPlatform

enum class ScreenShell { Lobby, Menu, InGame }

class Routed(val screen: Screen?, val consumed: Boolean) {
    companion object {
        fun show(screen: Screen?) = Routed(screen, false)
        val Consumed = Routed(null, true)
    }
}

object ScreenRouter {
    @JvmStatic
    fun route(screen: Screen?): Routed {
        if (screen == null) WorldRoutes.forget()
        if (screen == null || screen is ShellScreen) return Routed.show(screen)
        WorldRoutes.wrapperOf(screen)?.let { return it }
        RealmsRoutes.wrapperOf(screen)?.let { return it }
        val current = ClientPlatform.currentScreen()
        val origin = originOf(current)
        val inLobby = origin == ScreenShell.Lobby
        val config = PolyPlusScreensConfig
        val connection = if (isDeath(screen)) config.customDeath else config.customConnecting
        return (if (config.customSettings) SettingsRoutes.route(screen, origin) else null)
            ?: (if (config.customWorlds || inLobby) WorldRoutes.route(screen, origin) else null)
            ?: (if (connection) ConnectionRoutes.route(screen, origin) else null)
            ?: (if (config.customSettings) PackRoutes.route(screen, origin) else null)
            ?: (if (config.customRealms) RealmsRoutes.route(screen, origin) else null)
            ?: Routed.show(screen)
    }

    private fun isDeath(screen: Screen): Boolean {
        return screen.javaClass == DeathScreen::class.java
    }

    fun originOf(current: Screen?): ScreenShell? = when (current) {
        is PolyPlusLobbyScreen -> ScreenShell.Lobby
        is PolyPlusMainMenuScreen -> ScreenShell.Menu
        is OneClientPauseScreen -> ScreenShell.InGame
        is ShellScreen -> current.shell
        else -> null
    }

    fun contextShell(): ScreenShell =
        if (!inGame() && MainMenuReplacement.choice() == MainMenuChoice.LOBBY) ScreenShell.Lobby else fallbackShell()

    fun fallbackShell(): ScreenShell = if (inGame()) ScreenShell.InGame else ScreenShell.Menu

    private fun inGame(): Boolean = ClientPlatform.inWorld() && !LobbyScene.terrainActive()

    fun lobby(): PolyPlusLobbyScreen? = ClientPlatform.currentScreen() as? PolyPlusLobbyScreen
}
