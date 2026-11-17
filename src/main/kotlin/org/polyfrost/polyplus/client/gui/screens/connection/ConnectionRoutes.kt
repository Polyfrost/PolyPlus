package org.polyfrost.polyplus.client.gui.screens.connection

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.network.chat.contents.TranslatableContents
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.network.p2p.P2PConnectionContext
//?} elif = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.menu.multiplayer.ConnectScreen
import net.minecraft.client.gui.screen.menu.multiplayer.DisconnectedScreen
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.network.p2p.P2PConnectionContext
import org.polyfrost.polyplus.client.utils.ClientPlatform
*///?}
import org.polyfrost.polyplus.client.gui.screens.Routed
import org.polyfrost.polyplus.client.gui.screens.ScreenShell

object ConnectionRoutes {
    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        //? if > 1.8.9 {
        val shell = ScreenRouter.contextShell()
        val custom: Screen? = when (screen.javaClass) {
            ConnectScreen::class.java ->
                if (P2PConnectionContext.hasPendingJoin()) null else ConnectingScreen(shell, screen as ConnectScreen)
            DisconnectedScreen::class.java -> DisconnectedShellScreen(shell, screen as DisconnectedScreen)
            GenericMessageScreen::class.java ->
                if (startupMessage(screen)) null else StatusMessageScreen(shell, screen as GenericMessageScreen)
            else -> null
        }
        return custom?.let { Routed.show(it) }
        //?} elif = 1.8.9 {
        /*val type = screen.javaClass
        if (type != ConnectScreen::class.java && type != DisconnectedScreen::class.java) return null
        val shell = ScreenRouter.contextShell()
        if (!Minecraft.getInstance().isOnSameThread) {
            ClientPlatform.runOnMain { ClientPlatform.setScreen(screen) }
            return Routed.Consumed
        }
        val custom: Screen? = when (type) {
            ConnectScreen::class.java ->
                if (P2PConnectionContext.hasPendingJoin()) null else ConnectingScreen(shell, screen as ConnectScreen)
            DisconnectedScreen::class.java -> DisconnectedShellScreen(shell, screen as DisconnectedScreen)
            else -> null
        }
        return custom?.let { Routed.show(it) }
        *///?}
    }

    //? if > 1.8.9 {
    private fun startupMessage(screen: Screen): Boolean =
        (screen.title.contents as? TranslatableContents)?.key == "gui.loadingMinecraft"
    //?}
}
