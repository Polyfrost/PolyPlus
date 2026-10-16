package org.polyfrost.polyplus.client.emotes

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.internal.ui.components.asRenderText
import org.polyfrost.oneconfig.internal.ui.components.localizedTitle
import org.polyfrost.oneconfig.internal.ui.keybind.collectAllKeybindGroups
import org.polyfrost.polyplus.client.PolyPlusConfig
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
            //?} elif > 1.8.9 {
            /*mc.setScreen(EmoteWheelScreen())
            *///?} else {
            /*mc.openScreen(EmoteWheelScreen())
            *///?}
        }
    }

    fun isBound(): Boolean = PolyPlusConfig.emoteWheelKeybind.isBound

    fun conflictingKeybindNames(): List<String> {
        val wheel = PolyPlusConfig.emoteWheelKeybind
        if (!wheel.isBound) return emptyList()
        return runCatching {
            collectAllKeybindGroups().asSequence()
                .flatMap { it.entries.asSequence() }
                .mapNotNull { entry ->
                    val other = entry.prop.get() as? OneConfigKeybind ?: return@mapNotNull null
                    if (other === wheel || !wheel.conflictsWith(other)) return@mapNotNull null
                    entry.prop.localizedTitle().asRenderText().ifBlank { entry.path }
                }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())
    }
}
