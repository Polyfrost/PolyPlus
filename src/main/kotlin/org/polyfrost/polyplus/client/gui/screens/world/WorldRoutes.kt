package org.polyfrost.polyplus.client.gui.screens.world

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.AlertScreen
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.DirectJoinServerScreen
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform
//?} else {
/*import net.minecraft.client.gui.screen.menu.TitleScreen
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerScreen
import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen
import net.minecraft.client.gui.screen.menu.world.EditWorldScreen
import net.minecraft.client.gui.screen.menu.world.SelectWorldScreen
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
*///?}
import org.polyfrost.polyplus.client.PolyPlusScreensConfig
import org.polyfrost.polyplus.client.gui.screens.Routed
import org.polyfrost.polyplus.client.gui.screens.ScreenShell

object WorldRoutes {
    //? if > 1.8.9 {
    private val shells = HashMap<Screen, ShellScreen>()
    private var pending: ScreenShell? = null

    fun wrapperOf(screen: Screen): Routed? = shells[screen]?.let {
        pending = null
        Routed.show(it)
    }

    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        wrapperOf(screen)?.let { return it }
        if (ScreenRouter.originOf(screen) != null || isExit(screen)) shells.clear()
        val loading = ClientPlatform.currentScreen()?.javaClass == GenericMessageScreen::class.java
        if (screen.javaClass == GenericMessageScreen::class.java) {
            pending = origin ?: pending.takeIf { loading }
            return null
        }
        val shell = origin ?: pending.takeIf { loading }
        pending = null
        val ours = PolyPlusScreensConfig.customWorlds
        return when (screen.javaClass) {
            CreateWorldScreen::class.java -> if (ours) {
                remember(screen, CreateWorldShell(shell ?: ScreenRouter.fallbackShell(), WorldDraft26(screen as CreateWorldScreen)))
            } else null
            EditWorldScreen::class.java -> if (ours) {
                remember(screen, EditWorldShell(shell ?: ScreenRouter.fallbackShell(), EditWorldDraft26(screen as EditWorldScreen)))
            } else null
            DirectJoinServerScreen::class.java -> shell?.let { directJoin(screen as DirectJoinServerScreen, it) }
            ConfirmScreen::class.java -> shell?.let { Routed.show(ConfirmDialogShell(it, confirmSpec(screen as ConfirmScreen))) }
            AlertScreen::class.java -> shell?.let { Routed.show(ConfirmDialogShell(it, alertSpec(screen as AlertScreen))) }
            else -> null
        }
    }

    fun forget() {
        shells.clear()
        pending = null
    }

    private fun isExit(screen: Screen): Boolean = screen.javaClass.let {
        it == TitleScreen::class.java || it == SelectWorldScreen::class.java || it == JoinMultiplayerScreen::class.java
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
    //?} else {
    /*private val shells = HashMap<Screen, ShellScreen>()

    fun wrapperOf(screen: Screen): Routed? = shells[screen]?.let { Routed.show(it) }

    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        wrapperOf(screen)?.let { return it }
        if (ScreenRouter.originOf(screen) != null || isExit(screen)) shells.clear()
        if (!PolyPlusScreensConfig.customWorlds) return null
        val shell = origin ?: ScreenRouter.fallbackShell()
        return when (screen.javaClass) {
            CreateWorldScreen::class.java -> remember(screen, CreateWorldShell(shell, WorldDraft189(screen as CreateWorldScreen)))
            EditWorldScreen::class.java -> remember(screen, EditWorldShell(shell, EditWorldDraft189(screen as EditWorldScreen)))
            else -> null
        }
    }

    fun forget() {
        shells.clear()
    }

    private fun isExit(screen: Screen): Boolean = screen.javaClass.let {
        it == TitleScreen::class.java || it == SelectWorldScreen::class.java || it == MultiplayerScreen::class.java
    }

    private fun remember(screen: Screen, custom: ShellScreen): Routed {
        shells.clear()
        shells[screen] = custom
        return Routed.show(custom)
    }
    *///?}
}
