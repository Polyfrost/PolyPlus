@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.polyplus.client.gui.lobbyshell.EscapeCloses
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import kotlin.math.roundToInt
import org.polyfrost.oneconfig.api.ui.v1.theme.concentric

internal class ContextItem(
    val label: String,
    val icon: String? = null,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

private class OpenContextMenu(val at: IntOffset, val items: List<ContextItem>)

private class ContextMenuState {
    var open by mutableStateOf<OpenContextMenu?>(null)
}

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

private val LocalContextMenu = staticCompositionLocalOf<ContextMenuState?> { null }

private val MenuMinWidth = 170.dp
private val MenuInset = 6.dp
private val MenuMaxWidth = 280.dp

@Composable
internal fun ContextMenuHost(content: @Composable () -> Unit) {
    val state = remember { ContextMenuState() }
    CompositionLocalProvider(LocalContextMenu provides state, content = content)
    val menu = state.open ?: return
    key(menu) {
        val close = { if (state.open === menu) state.open = null }
        EscapeCloses(close)
        Popup(
            popupPositionProvider = remember(menu.at) { CursorPosition(menu.at) },
            onDismissRequest = close,
            properties = PopupProperties(focusable = true),
        ) {
            ContextMenuList(menu.items, close)
        }
    }
}

@Composable
internal fun ContextMenuList(items: List<ContextItem>, onPicked: () -> Unit) {
    Column(
        Modifier.widthIn(min = MenuMinWidth, max = MenuMaxWidth).width(IntrinsicSize.Max).popup().padding(MenuInset),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { item ->
            SocialMenuItem(
                item.label,
                item.icon,
                color = if (item.danger) SocialDangerText else SocialTextPrimary,
                enabled = item.enabled,
                shape = SocialWindowShape.concentric(MenuInset),
            ) {
                onPicked()
                item.onClick()
            }
        }
    }
}

@Composable
internal fun Modifier.contextMenu(items: () -> List<ContextItem>): Modifier {
    val state = LocalContextMenu.current ?: return this
    val latest by rememberUpdatedState(items)
    val holder = remember { CoordinatesHolder() }
    return this
        .onGloballyPositioned { holder.coordinates = it }
        .pointerInput(state) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.type != PointerEventType.Press || !event.secondaryPress()) continue
                    val change = event.changes.firstOrNull()?.takeUnless { it.isConsumed } ?: continue
                    val coordinates = holder.coordinates?.takeIf { it.isAttached } ?: continue
                    val list = latest()
                    if (list.isEmpty()) continue
                    change.consume()
                    val point = coordinates.localToWindow(change.position)
                    state.open = OpenContextMenu(IntOffset(point.x.roundToInt(), point.y.roundToInt()), list)
                }
            }
        }
}

private fun PointerEvent.secondaryPress(): Boolean = buttons.isSecondaryPressed && !buttons.isPrimaryPressed

private class CursorPosition(private val at: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = if (at.x + popupContentSize.width <= windowSize.width) at.x else at.x - popupContentSize.width
        val y = if (at.y + popupContentSize.height <= windowSize.height) at.y else at.y - popupContentSize.height
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
