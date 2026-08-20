//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.emotes

import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.gui.EmoteWheelScreen

object EmoteWheelKeybind {
    @Volatile
    private var held = false

    fun isHeld(): Boolean = held

    fun onKeybindState(pressed: Boolean) {
        held = pressed
        if (!pressed) return

        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        //? if >= 26.2 {
        val currentScreen = mc.gui.screen()
        //?} else {
        /*val currentScreen = mc.screen
        *///?}
        if (currentScreen == null) {
            //? if >= 26.2 {
            mc.gui.setScreen(EmoteWheelScreen())
            //?} else {
            /*mc.setScreen(EmoteWheelScreen())
            *///?}
        }
    }
}
//?}
