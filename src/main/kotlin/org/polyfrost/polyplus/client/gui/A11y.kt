@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y

internal enum class A11yRegion { Tour, Nav, Tools, Banner, Play, Page, Bar, Scene }

internal object LobbyA11y {
    private var rootFocused = false

    private val regions = HashMap<A11yRegion, FocusRequester>()

    fun keyboardFocused(): Boolean = A11y.keyboardMode && rootFocused

    fun frame() = Unit

    internal fun rootFocusChanged(focused: Boolean) {
        rootFocused = focused
    }

    internal fun attachRegion(region: A11yRegion, requester: FocusRequester) {
        regions[region] = requester
    }

    internal fun detachRegion(region: A11yRegion, requester: FocusRequester) {
        if (regions[region] === requester) regions.remove(region)
    }

    internal fun enterRegions(direction: FocusDirection, after: A11yRegion?): Boolean {
        val order = A11yRegion.entries
        val forward = direction == FocusDirection.Next
        val start = after?.ordinal ?: if (forward) -1 else order.size
        for (step in 1..order.size) {
            val index = if (forward) start + step else start - step
            val region = order[index.mod(order.size)]
            if (region == after) continue
            val requester = regions[region] ?: continue
            if (requester.requestFocus(direction)) return true
        }
        return false
    }
}

private fun FocusDirection.isTab(): Boolean = this == FocusDirection.Next || this == FocusDirection.Previous

internal fun Modifier.a11yRegion(region: A11yRegion): Modifier = composed {
    val requester = remember { FocusRequester() }
    DisposableEffect(region, requester) {
        LobbyA11y.attachRegion(region, requester)
        onDispose { LobbyA11y.detachRegion(region, requester) }
    }
    focusRequester(requester)
        .focusProperties {
            onExit = {
                if (requestedFocusDirection.isTab()) LobbyA11y.enterRegions(requestedFocusDirection, region)
            }
        }
        .focusGroup()
}

internal fun Modifier.a11yRoot(): Modifier = composed {
    val focus = LocalFocusManager.current
    DisposableEffect(Unit) { onDispose { LobbyA11y.rootFocusChanged(false) } }
    focusProperties {
        onEnter = {
            if (requestedFocusDirection.isTab()) LobbyA11y.enterRegions(requestedFocusDirection, null)
        }
    }
        .onFocusChanged { LobbyA11y.rootFocusChanged(it.hasFocus) }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !A11y.keyboardMode) return@onKeyEvent false
            val direction = when (event.key) {
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                else -> return@onKeyEvent false
            }
            focus.moveFocus(direction)
        }
        .focusGroup()
}

internal val LocalAnnounceTitles = staticCompositionLocalOf { false }
