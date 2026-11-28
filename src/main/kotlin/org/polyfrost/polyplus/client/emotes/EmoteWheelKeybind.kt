@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.emotes

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusControllers
import org.polyfrost.polyplus.client.gui.EmoteWheelOverlay
import org.polyfrost.polyplus.client.gui.WheelOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform

object EmoteWheelKeybind {
    @Volatile
    private var keyHeld = false

    @Volatile
    private var controllerHeld = false

    fun isHeld(): Boolean = keyHeld || controllerHeld

    fun onKeybindState(pressed: Boolean) {
        keyHeld = pressed
        if (pressed) open()
    }

    fun onControllerState(pressed: Boolean) {
        controllerHeld = pressed
        if (pressed) open()
    }

    private fun open() {
        if (WheelOverlay.isOpen) return
        if (Minecraft.getInstance().player == null || ClientPlatform.currentScreen() != null) return
        EmoteWheelOverlay.open()
    }

    fun isBound(): Boolean = PolyPlusConfig.emoteWheelKeybind.isBound

    fun canBindController(): Boolean = PolyPlusControllers.canBindEmoteWheel()

    fun conflictingKeybindNames(): List<String> {
        val wheel = PolyPlusConfig.emoteWheelKeybind
        if (!wheel.isBound) return emptyList()
        return runCatching {
            KeybindManager.configKeybinds().asSequence()
                .mapNotNull { entry ->
                    if (entry.keybind === wheel || !wheel.conflictsWith(entry.keybind)) return@mapNotNull null
                    entry.title
                }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())
    }
}
