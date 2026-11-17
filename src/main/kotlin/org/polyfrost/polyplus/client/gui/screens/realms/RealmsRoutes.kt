package org.polyfrost.polyplus.client.gui.screens.realms

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
//? if >= 26.3 {
import com.mojang.realmsclient.RealmsMainScreen
//?}
import org.polyfrost.polyplus.client.gui.screens.Routed
import org.polyfrost.polyplus.client.gui.screens.ScreenShell

object RealmsRoutes {
    //? if >= 26.3 {
    private var wrapped: RealmsShellScreen? = null

    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        if (screen.javaClass != RealmsMainScreen::class.java) return null
        wrapped?.takeIf { it.vanilla === screen }?.let { return Routed.show(it) }
        if (origin == null || origin == ScreenShell.InGame) return null
        val custom = RealmsShellScreen(origin, screen as RealmsMainScreen)
        wrapped = custom
        return Routed.show(custom)
    }
    //?} else {
    /*fun route(screen: Screen, origin: ScreenShell?): Routed? = null
    *///?}
}
