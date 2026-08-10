//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.emotes

import com.mojang.blaze3d.platform.InputConstants
//? if >= 26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
*///?}
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.gui.EmoteWheelScreen

object EmoteWheelKeybind {
    private val logger = LogManager.getLogger("polyplus/emote-wheel")
    private lateinit var keyMapping: KeyMapping

    fun isHeld(): Boolean {
        val key = InputConstants.getKey(keyMapping.saveString())
        if (key.type == InputConstants.Type.MOUSE) {
            return keyMapping.isDown
        }
        val mc = Minecraft.getInstance()
        //? if >= 1.21.10 {
        val down = InputConstants.isKeyDown(mc.window, key.value)
        //?} else {
        /*val down = InputConstants.isKeyDown(mc.window.window, key.value)
        *///?}
        return down
    }

    fun register() {
        val mapping = KeyMapping(
            "key.polyplus.emote_wheel",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.value,
            //? if >= 1.21.10 {
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "category")),
            //?} else {
            /*"key.polyplus.category",
            *///?}
        )
        keyMapping =
            //? if >= 26.1 {
            KeyMappingHelper.registerKeyMapping(mapping)
            //?} else {
            /*KeyBindingHelper.registerKeyBinding(mapping)
            *///?}

        eventHandler { _: TickEvent.End ->
            val mc = Minecraft.getInstance()
            if (mc.player == null) return@eventHandler
            while (keyMapping.consumeClick()) {
                logger.info("consumeClick() fired")
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
    }
}
//?}
