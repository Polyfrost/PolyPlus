package org.polyfrost.polyplus.client.gui.screens.realms

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
//? if > 1.8.9 {
import com.mojang.realmsclient.RealmsMainScreen
//?}
import org.polyfrost.polyplus.client.gui.screens.Routed
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.gui.screens.ScreenShell

object RealmsRoutes {
    //? if > 1.8.9 {
    private var wrapped: RealmsShellScreen? = null

    fun wrapperOf(screen: Screen): Routed? = wrapped?.takeIf { it.vanilla === screen }?.let { Routed.show(it) }

    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        if (screen.javaClass != RealmsMainScreen::class.java) return null
        wrapperOf(screen)?.let { return it }
        val shell = origin ?: ScreenRouter.fallbackShell()
        if (shell == ScreenShell.InGame) return null
        val custom = RealmsShellScreen(shell, screen as RealmsMainScreen)
        wrapped = custom
        return Routed.show(custom)
    }
    //?} else {
    /*fun wrapperOf(screen: Screen): Routed? = null

    fun route(screen: Screen, origin: ScreenShell?): Routed? = null
    *///?}
}
