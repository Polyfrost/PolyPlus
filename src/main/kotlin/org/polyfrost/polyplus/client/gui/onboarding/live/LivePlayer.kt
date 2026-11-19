@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.onboarding.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.minecraft.client.Minecraft
//? if = 1.8.9 {
/*import org.lwjgl.input.Keyboard
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.KeyCap
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialTextSecondary

@Composable
internal fun LivePlayerPreview(tweak: LiveTweak, ours: Boolean, modifier: Modifier) {
    val stage = remember(tweak, ours) { LivePlayerStage(tweak, ours) }
    val slot = remember(stage) { RenderBlocksSlot("polyplus_live_player", stage) }
    DisposableEffect(stage) {
        LivePlayerScene.attach(stage.actor)
        onDispose { LivePlayerScene.detach(stage.actor) }
    }
    val frameNanos by produceState(0L) { while (true) withFrameNanos { value = it } }
    stage.horseOpacity = LocalLiveTuning.current.horseOpacity
    Box(modifier) {
        RenderBlocksCanvas(slot, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().drawBehind {
                if (frameNanos == 0L) return@drawBehind
                val fade = stage.actor.fadeAlpha(LivePlayerScene.partial())
                if (fade > 0f) drawRect(Color.Black.copy(alpha = fade))
            },
        )
        if (tweak == LiveTweak.SPRINT && frameNanos != 0L) {
            SprintHint(stage.actor, ours, Modifier.align(Alignment.TopCenter).padding(top = KEY_INSET.dp))
        }
    }
}

@Composable
private fun SprintHint(actor: LivePlayerActor, ours: Boolean, modifier: Modifier) {
    val down = actor.keyDownState
    val sprinting = actor.sprintingState
    val theme = OneConfigTheme.current
    //? if > 1.8.9 {
    val label = remember { runCatching { Minecraft.getInstance().options.keySprint.translatedKeyMessage.string }.getOrDefault("Ctrl") }
    //?} else {
    /*val label = remember { runCatching { legacyKeyName(Minecraft.getInstance().options.sprintKey.keyCode) }.getOrDefault("Left Control") }
    *///?}
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        KeyCap(
            Modifier.graphicsLayer {
                translationY = if (down) KEY_TRAVEL * density else 0f
                alpha = if (down) KEY_DOWN_ALPHA else 1f
            },
            height = KEY_HEIGHT.dp,
        ) {
            Text(
                label,
                Modifier.padding(horizontal = KEY_PAD.dp),
                color = theme.textColor,
                style = theme.typography.body.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            if (ours) TAP_CAPTION else HOLD_CAPTION,
            Modifier.padding(top = CAPTION_GAP.dp),
            color = SocialTextSecondary,
            style = theme.typography.caption,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            if (sprinting) SPRINTING_LABEL else WALKING_LABEL,
            Modifier.padding(top = STATE_GAP.dp),
            color = SocialTextPrimary,
            style = theme.typography.smallMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

//? if = 1.8.9 {
/*private fun legacyKeyName(code: Int): String {
    if (code < 0) {
        return when (val button = code + MOUSE_OFFSET) {
            1 -> "Left Button"
            2 -> "Right Button"
            3 -> "Middle Button"
            else -> "Mouse $button"
        }
    }
    val name = Keyboard.getKeyName(code) ?: return "Key $code"
    LEGACY_KEY_NAMES[name]?.let { return it }
    if (name.startsWith("NUMPAD") && name.length > NUMPAD_PREFIX) return "Keypad " + name.substring(NUMPAD_PREFIX)
    if (name.length == 1 || (name.startsWith("F") && name.drop(1).all(Char::isDigit))) return name
    return name.lowercase().replaceFirstChar(Char::uppercaseChar)
}

private const val MOUSE_OFFSET = 101
private const val NUMPAD_PREFIX = 6
private val LEGACY_KEY_NAMES = mapOf(
    "LCONTROL" to "Left Control",
    "RCONTROL" to "Right Control",
    "LSHIFT" to "Left Shift",
    "RSHIFT" to "Right Shift",
    "LMENU" to "Left Alt",
    "RMENU" to "Right Alt",
    "LMETA" to "Left Win",
    "RMETA" to "Right Win",
    "SPACE" to "Space",
    "RETURN" to "Enter",
    "NUMPADENTER" to "Keypad Enter",
    "TAB" to "Tab",
    "CAPITAL" to "Caps Lock",
    "BACK" to "Backspace",
    "ESCAPE" to "Escape",
    "UP" to "Up Arrow",
    "DOWN" to "Down Arrow",
    "LEFT" to "Left Arrow",
    "RIGHT" to "Right Arrow",
    "INSERT" to "Insert",
    "DELETE" to "Delete",
    "HOME" to "Home",
    "END" to "End",
    "PRIOR" to "Page Up",
    "NEXT" to "Page Down",
    "GRAVE" to "`",
    "MINUS" to "-",
    "EQUALS" to "=",
    "LBRACKET" to "[",
    "RBRACKET" to "]",
    "BACKSLASH" to "\\",
    "SEMICOLON" to ";",
    "APOSTROPHE" to "'",
    "COMMA" to ",",
    "PERIOD" to ".",
    "SLASH" to "/",
    "ADD" to "Keypad +",
    "SUBTRACT" to "Keypad -",
    "MULTIPLY" to "Keypad *",
    "DIVIDE" to "Keypad /",
    "DECIMAL" to "Keypad .",
    "NUMLOCK" to "Num Lock",
    "SCROLL" to "Scroll Lock",
    "PAUSE" to "Pause",
)
*///?}

private const val KEY_INSET = 16f
private const val HOLD_CAPTION = "Hold"
private const val TAP_CAPTION = "Tap once"
private const val SPRINTING_LABEL = "Sprinting"
private const val WALKING_LABEL = "Walking"
private const val CAPTION_GAP = 6f
private const val STATE_GAP = 10f
private const val KEY_HEIGHT = 40f
private const val KEY_PAD = 12f
private const val KEY_TRAVEL = 2f
private const val KEY_DOWN_ALPHA = 0.8f
