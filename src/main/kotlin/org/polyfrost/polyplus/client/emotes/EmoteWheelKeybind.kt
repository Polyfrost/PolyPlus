@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.emotes

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.EmoteWheelOverlay
import org.polyfrost.polyplus.client.gui.WheelOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform

object EmoteWheelKeybind {
    @Volatile
    private var held = false

    fun isHeld(): Boolean = held

    fun onKeybindState(pressed: Boolean) {
        held = pressed
        if (!pressed || WheelOverlay.isOpen) return
        if (Minecraft.getInstance().player == null || ClientPlatform.currentScreen() != null) return
        EmoteWheelOverlay.open()
    }

    fun isBound(): Boolean = PolyPlusConfig.emoteWheelKeybind.isBound

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
