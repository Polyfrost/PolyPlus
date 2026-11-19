package org.polyfrost.polyplus.client.gui.screens.worldoptions

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
//? if >= 26.3 {
import net.minecraft.client.gui.screens.WorldOptionsScreen
//?} elif >= 26.1 {
/*import net.minecraft.client.gui.screens.options.WorldOptionsScreen
*///?}
import org.polyfrost.polyplus.client.gui.screens.Routed
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.utils.ClientPlatform

object WorldOptionsRoutes {
    //? if >= 26.1 {
    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        if (screen.javaClass != WorldOptionsScreen::class.java) return null
        return WorldOptions.create(ClientPlatform.currentScreen(), origin)?.let { Routed.show(it) }
    }
    //?} else {
    /*fun route(screen: Screen, origin: ScreenShell?): Routed? = null
    *///?}
}
