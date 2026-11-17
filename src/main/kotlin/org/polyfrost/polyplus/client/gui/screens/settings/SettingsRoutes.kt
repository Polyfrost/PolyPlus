package org.polyfrost.polyplus.client.gui.screens.settings

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen
import org.polyfrost.polyplus.client.gui.screens.Routed
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.Collections
import java.util.WeakHashMap

object SettingsRoutes {
    private var passing: Screen? = null
    private val kept: MutableSet<Screen> = Collections.newSetFromMap(WeakHashMap())

    internal fun pass(screen: Screen) {
        passing = screen
        ClientPlatform.setScreen(screen)
    }

    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        if (screen === passing) {
            passing = null
            if (SettingsPlatform.replaces(screen)) kept += screen
            return null
        }
        if (screen in kept) return null
        if (!SettingsPlatform.replaces(screen)) return null
        val shell = origin ?: ScreenRouter.fallbackShell()
        if (origin == ScreenShell.InGame) {
            (ClientPlatform.currentScreen() as? OneClientPauseScreen)?.let { pause ->
                pause.openGameSettings()
                return Routed.Consumed
            }
        }
        if (origin == ScreenShell.Lobby) {
            ScreenRouter.lobby()?.lobby?.let { lobby ->
                lobby.show(LobbyPage.Settings)
                return Routed.Consumed
            }
        }
        return Routed.show(SettingsScreen(shell, SettingsPlatform.parentOf(screen)))
    }
}
