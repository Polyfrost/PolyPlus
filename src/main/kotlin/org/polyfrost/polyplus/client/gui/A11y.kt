@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y
import org.polyfrost.oneconfig.api.ui.v1.components.focusRegion
import org.polyfrost.oneconfig.api.ui.v1.components.focusRoot
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

internal enum class A11yRegion { Tour, Nav, Tools, Banner, Play, Page, Bar, Scene }

internal object LobbyA11y {
    private var rootFocused = false

    fun keyboardFocused(): Boolean = A11y.keyboardMode && rootFocused

    fun frame() = Unit

    internal fun rootFocusChanged(focused: Boolean) {
        rootFocused = focused
    }
}

private fun KeyEvent.isTabDown(): Boolean = type == KeyEventType.KeyDown && key == Key.Tab

internal fun FocusManager.moveTabFocus(backward: Boolean): Boolean {
    val direction = if (backward) FocusDirection.Previous else FocusDirection.Next
    if (moveFocus(direction)) return true
    clearFocus()
    return moveFocus(direction)
}

internal fun Modifier.wrapTabFocus(): Modifier = composed {
    val focus = LocalFocusManager.current
    onKeyEvent { event -> event.isTabDown() && focus.moveTabFocus(event.isShiftPressed) }
}

internal fun Modifier.a11yRegion(region: A11yRegion): Modifier = focusRegion(region.ordinal)

internal fun Modifier.a11yRoot(): Modifier = composed {
    DisposableEffect(Unit) { onDispose { LobbyA11y.rootFocusChanged(false) } }
    onFocusChanged { LobbyA11y.rootFocusChanged(it.hasFocus) }
        .trackScreenFocus()
        .focusRoot()
}

internal object ScreenFocus {
    private val holders = Collections.synchronizedMap(WeakHashMap<Any, WeakReference<Any>>())

    fun holds(screen: Any?): Boolean {
        if (screen == null || holders.isEmpty()) return false
        synchronized(holders) {
            return holders.values.any { it.get() === screen }
        }
    }

    internal fun changed(token: Any, focused: Boolean) {
        val screen = ClientPlatform.currentScreen()
        if (focused && screen != null) holders[token] = WeakReference(screen) else holders.remove(token)
    }
}

internal fun Modifier.trackScreenFocus(): Modifier = composed {
    val token = remember { Any() }
    DisposableEffect(token) { onDispose { ScreenFocus.changed(token, false) } }
    onFocusChanged { ScreenFocus.changed(token, it.hasFocus) }
}
