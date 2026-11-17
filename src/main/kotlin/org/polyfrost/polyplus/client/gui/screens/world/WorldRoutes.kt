package org.polyfrost.polyplus.client.gui.screens.world

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
//? if >= 26.3 {
import net.minecraft.client.gui.screens.AlertScreen
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.DirectJoinServerScreen
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform
//?} elif = 1.8.9 {
/*import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen
import net.minecraft.client.gui.screen.menu.world.EditWorldScreen
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
*///?}
import org.polyfrost.polyplus.client.gui.screens.Routed
import org.polyfrost.polyplus.client.gui.screens.ScreenShell

object WorldRoutes {
    //? if >= 26.3 {
    private val shells = HashMap<Screen, ShellScreen>()
    private var pending: ScreenShell? = null

    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        shells[screen]?.let {
            pending = null
            return Routed.show(it)
        }
        if (ScreenRouter.originOf(screen) != null) shells.clear()
        val loading = ClientPlatform.currentScreen()?.javaClass == GenericMessageScreen::class.java
        if (screen.javaClass == GenericMessageScreen::class.java) {
            pending = origin ?: pending.takeIf { loading }
            return null
        }
        val shell = origin ?: pending.takeIf { loading }
        pending = null
        if (shell == null) return null
        return when (screen.javaClass) {
            CreateWorldScreen::class.java -> remember(screen, CreateWorldShell(shell, WorldDraft26(screen as CreateWorldScreen)))
            EditWorldScreen::class.java -> remember(screen, EditWorldShell(shell, EditWorldDraft26(screen as EditWorldScreen)))
            DirectJoinServerScreen::class.java -> directJoin(screen as DirectJoinServerScreen, shell)
            ConfirmScreen::class.java -> Routed.show(ConfirmDialogShell(shell, confirmSpec(screen as ConfirmScreen)))
            AlertScreen::class.java -> Routed.show(ConfirmDialogShell(shell, alertSpec(screen as AlertScreen)))
            else -> null
        }
    }

    private fun remember(screen: Screen, custom: ShellScreen): Routed {
        shells.clear()
        shells[screen] = custom
        return Routed.show(custom)
    }

    private fun directJoin(screen: DirectJoinServerScreen, shell: ScreenShell): Routed {
        val lobby = ClientPlatform.currentScreen() as? PolyPlusLobbyScreen
        if (shell == ScreenShell.Lobby && lobby != null) {
            lobby.showPage(LobbyPage.Multiplayer)
            lobby.lobby.directOpen = true
            return Routed.Consumed
        }
        return remember(screen, DirectJoinShell(shell, DirectJoinDraft26(screen)))
    }
    //?} elif = 1.8.9 {
    /*private val shells = HashMap<Screen, ShellScreen>()

    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        shells[screen]?.let { return Routed.show(it) }
        if (ScreenRouter.originOf(screen) != null) shells.clear()
        val shell = origin ?: return null
        return when (screen.javaClass) {
            CreateWorldScreen::class.java -> remember(screen, CreateWorldShell(shell, WorldDraft189(screen as CreateWorldScreen)))
            EditWorldScreen::class.java -> remember(screen, EditWorldShell(shell, EditWorldDraft189(screen as EditWorldScreen)))
            else -> null
        }
    }

    private fun remember(screen: Screen, custom: ShellScreen): Routed {
        shells.clear()
        shells[screen] = custom
        return Routed.show(custom)
    }
    *///?} else {
    /*fun route(screen: Screen, origin: ScreenShell?): Routed? = null
    *///?}
}
