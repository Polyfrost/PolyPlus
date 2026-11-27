@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.emotes

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus
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
        if (Minecraft.getInstance().player != null && ClientPlatform.currentScreen() == null) {
            EmoteWheelOverlay.open()
            return
        }
        EmoteWheelOverlay.openOverLobby()
    }

    fun isBound(): Boolean = PolyPlusConfig.emoteWheelKeybind.isBound

    fun claimsKey(key: Int, modifiers: Int): Boolean {
        val wheel = PolyPlusConfig.emoteWheelKeybind
        if (!wheel.isBound || wheel.keyCodes?.contains(key) != true || TextInputFocus.isTyping) return false
        return wheel.test(KeybindManager.heldKeys + key, emptySet(), (modifiers and MODIFIER_MASK).toByte())
    }

    fun claimsButton(button: Int): Boolean {
        val wheel = PolyPlusConfig.emoteWheelKeybind
        return wheel.isBound && !TextInputFocus.isTyping && KeybindManager.isTriggeredByMouse(wheel, button)
    }

    private const val MODIFIER_MASK = 0xF

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
