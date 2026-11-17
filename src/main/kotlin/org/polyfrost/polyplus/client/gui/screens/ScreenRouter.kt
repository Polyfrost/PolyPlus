package org.polyfrost.polyplus.client.gui.screens

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.PolyPlusScreensConfig
//? if >= 26.3 {
import net.minecraft.client.gui.screens.DeathScreen
//?} elif = 1.8.9 {
/*import net.minecraft.client.gui.screen.game.DeathScreen
*///?}
import org.polyfrost.polyplus.client.MainMenuChoice
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen
import org.polyfrost.polyplus.client.gui.pausescreen.PauseScreenStyle
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
        if (screen == null || screen is ShellScreen) return Routed.show(screen)
        val origin = originOf(ClientPlatform.currentScreen())
        val config = PolyPlusScreensConfig
        val inLobby = origin == ScreenShell.Lobby
        val connection = if (isDeath(screen)) config.customDeath else config.customConnecting || contextShell() == ScreenShell.Lobby
        return (if (config.customSettings || inLobby) SettingsRoutes.route(screen, origin) else null)
            ?: (if (config.customWorlds || inLobby) WorldRoutes.route(screen, origin) else null)
            ?: (if (connection) ConnectionRoutes.route(screen, origin) else null)
            ?: (if (config.customSettings || inLobby) PackRoutes.route(screen, origin) else null)
            ?: (if (config.customRealms || inLobby) RealmsRoutes.route(screen, origin) else null)
            ?: Routed.show(screen)
    }

    private fun isDeath(screen: Screen): Boolean {
        //? if >= 26.3 {
        return screen.javaClass == DeathScreen::class.java
        //?} elif = 1.8.9 {
        /*return screen.javaClass == DeathScreen::class.java
        *///?} else {
        /*return false
        *///?}
    }

    fun originOf(current: Screen?): ScreenShell? = when (current) {
        is PolyPlusLobbyScreen -> ScreenShell.Lobby
        is PolyPlusMainMenuScreen -> ScreenShell.Menu
        is OneClientPauseScreen -> ScreenShell.InGame
        is ShellScreen -> current.shell
        else -> null
    }

    fun contextShell(): ScreenShell? =
        if (ClientPlatform.inWorld() && !LobbyScene.terrainActive()) {
            if (PolyPlusPauseConfig.pauseScreenStyle != PauseScreenStyle.VANILLA) ScreenShell.InGame else null
        } else {
            when (MainMenuReplacement.choice()) {
                MainMenuChoice.LOBBY -> ScreenShell.Lobby
                MainMenuChoice.POLYPLUS -> ScreenShell.Menu
                MainMenuChoice.VANILLA -> null
            }
        }

    fun lobby(): PolyPlusLobbyScreen? = ClientPlatform.currentScreen() as? PolyPlusLobbyScreen
}
