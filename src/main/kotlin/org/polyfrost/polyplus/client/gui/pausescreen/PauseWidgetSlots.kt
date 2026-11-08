@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ContextMenuHost
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.ReorderState
import org.polyfrost.oneconfig.api.ui.v1.components.rememberReorderState
import org.polyfrost.oneconfig.api.ui.v1.components.reorderItem
import org.polyfrost.oneconfig.api.ui.v1.components.withItemMoved
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout

private val BAR_RULE = 24.dp
private val CARD_GAP = 2.dp
private val CARD_GLYPH = 16.dp
private const val LIFTED_ALPHA = 0.85f
private val PANEL_RADIUS = 16.dp
private val PANEL_PADDING = 10.dp
private val PANEL_RISE = 12.dp
private const val PANEL_ENTER_MILLIS = 180

internal interface FullBleedPanel

private class WidgetDrag(val state: ReorderState, val shown: List<PauseWidget>)

private fun List<PauseWidget>.movedBy(id: String, by: Int): List<String>? {
    val ids = map { it.id }
    val from = ids.indexOf(id)
    return ids.withItemMoved(from, from + by)
}

@Composable
private fun rememberWidgetDrag(
    orientation: Orientation,
    widgets: List<PauseWidget>,
    refresh: () -> Unit,
    commit: (List<String>) -> Unit = PauseWidgetRegistry::reorder,
): WidgetDrag {
    var dragged by remember { mutableStateOf<List<PauseWidget>?>(null) }
    val shown = dragged ?: widgets
    val current by rememberUpdatedState(shown)
    val latest by rememberUpdatedState(refresh)
    val store by rememberUpdatedState(commit)
    val state = rememberReorderState(
        orientation,
        { from, to -> current.withItemMoved(from, to)?.let { dragged = it } },
        {
            dragged?.let { order ->
                dragged = null
                store(order.map { it.id })
                latest()
            }
        },
    )
    return WidgetDrag(state, shown)
}

private fun Modifier.reorderable(drag: WidgetDrag, id: String, index: Int): Modifier = this
    .reorderItem(drag.state, id, index)
    .graphicsLayer { if (drag.state.draggingKey == id) alpha = LIFTED_ALPHA }

private fun widgetMenu(
    widget: PauseWidget,
    current: List<PauseWidget>,
    vertical: Boolean,
    refresh: () -> Unit,
    commit: (List<String>) -> Unit = PauseWidgetRegistry::reorder,
    asPanel: Boolean? = null,
): List<MenuAction> = buildList {
    val reorder: (List<String>?) -> Unit = { ids ->
        if (ids != null) {
            commit(ids)
            refresh()
        }
    }
    if (asPanel != null) {
        add(MenuAction(if (asPanel) "Show as panel" else "Show in bar") {
            PauseWidgetRegistry.setPanel(widget.id, asPanel)
            refresh()
        })
    }
    current.movedBy(widget.id, -1)?.let { ids ->
        add(MenuAction(if (vertical) "Move up" else "Move left", asset(if (vertical) "chevron-up" else "chevron-left")) { reorder(ids) })
    }
    current.movedBy(widget.id, 1)?.let { ids ->
        add(MenuAction(if (vertical) "Move down" else "Move right", if (vertical) null else asset("chevron-right")) { reorder(ids) })
    }
    add(MenuAction("Remove widget", asset("trash-01"), danger = true) {
        PauseWidgetRegistry.remove(widget.id)
        refresh()
    })
}

@Composable
private fun AddWidget(
    addable: List<PauseWidget>,
    side: Boolean,
    refresh: () -> Unit,
    trigger: @Composable (press: () -> Unit, open: Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        trigger({ open = !open }, open)
        if (open) {
            val close = { open = false }
            EscapeCloses(close)
            Popup(
                popupPositionProvider = rememberToolAnchor(side),
                onDismissRequest = close,
                properties = PopupProperties(focusable = true),
            ) {
                MenuList(
                    addable.map { widget ->
                        MenuAction(widget.title, widget.icon.ifEmpty { null }) {
                            PauseWidgetRegistry.add(widget.id)
                            refresh()
                        }
                    },
                    close,
                )
            }
        }
    }
}

@Composable
private fun BarRule() {
    Box(Modifier.width(1.dp).height(BAR_RULE).background(SocialBorderColor))
}

@Composable
internal fun PauseWidgetBar(
    screen: OneClientPauseScreen,
    widgets: PauseWidgetsHandle = rememberPauseWidgets(),
    panelled: Set<String> = emptySet(),
    panelArea: Boolean = false,
) {
    val view = widgets.view
    if (view.empty) return
    val context = remember(screen) { PauseScreenWidgetContext(screen) }
    val drag = rememberWidgetDrag(Orientation.Horizontal, view.added.filter { it.id !in panelled }, widgets.refresh)
    val added = drag.shown
    val panelModes = view.panels.mapTo(HashSet()) { it.id }
    Box {
        ContextMenuHost {
            Row(
                Modifier.height(LobbyLayout.BarHeight).pauseGlass(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                view.builtIns.forEachIndexed { index, widget ->
                    key(widget.id) {
                        if (index > 0) BarRule()
                        widget.Bar(context)
                    }
                }
                added.forEachIndexed { index, widget ->
                    key(widget.id) {
                        Row(
                            Modifier
                                .widgetGuard(widget.id)
                                .reorderable(drag, widget.id, index)
                                .contextMenu { widgetMenu(widget, added, false, widgets.refresh, asPanel = barPanelToggle(widget, panelModes, panelArea)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (index > 0 || view.builtIns.isNotEmpty()) BarRule()
                            WidgetGuard(widget.id) { widget.Bar(context) }
                        }
                    }
                }
                if (view.addable.isNotEmpty()) {
                    if (view.builtIns.isNotEmpty() || added.isNotEmpty()) BarRule()
                    AddWidget(view.addable, side = false, refresh = widgets.refresh) { press, open ->
                        BarTile(tooltip = if (open) null else "Add widget", active = open, onClick = press, glass = false) { hovered ->
                            PauseGlyph(asset("plus"), if (hovered || open) SocialTextPrimary else MutedText, 20.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PauseWidgetCard(screen: OneClientPauseScreen, modifier: Modifier, width: Dp) {
    val widgets = rememberPauseWidgets()
    val view = widgets.view
    if (view.empty) return
    val context = remember(screen) { PauseScreenWidgetContext(screen) }
    val drag = rememberWidgetDrag(Orientation.Vertical, view.added, widgets.refresh)
    val added = drag.shown
    Box(modifier) {
        ContextMenuHost {
            Column(
                Modifier.width(width).pauseGlass(16.dp).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
            ) {
                view.builtIns.forEach { widget ->
                    key(widget.id) { widget.Card(context) }
                }
                added.forEachIndexed { index, widget ->
                    key(widget.id) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .widgetGuard(widget.id)
                                .reorderable(drag, widget.id, index)
                                .contextMenu { widgetMenu(widget, added, true, widgets.refresh) },
                        ) {
                            WidgetGuard(widget.id) { widget.Card(context) }
                        }
                    }
                }
                if (view.addable.isNotEmpty()) {
                    AddWidget(view.addable, side = true, refresh = widgets.refresh) { press, open ->
                        CardRow(active = open, onClick = press) { lit ->
                            Box(Modifier.size(CARD_GLYPH), contentAlignment = Alignment.Center) {
                                PauseGlyph(asset("plus"), if (lit) SocialTextPrimary else MutedText, CARD_GLYPH)
                            }
                            Label("Add widget", 13.sp, color = if (lit) SocialTextPrimary else MutedText, weight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private fun PauseWidget.fullBleed(): Boolean = this is FullBleedPanel || (this as? RegisteredPauseWidget)?.fullBleed == true

private fun barPanelToggle(widget: PauseWidget, panelModes: Set<String>, panelArea: Boolean): Boolean? = with(PauseWidgetRegistry) {
    when {
        !widget.panelCapable() -> null
        widget.id in panelModes -> false
        panelArea -> true
        else -> null
    }
}

@Composable
internal fun PauseWidgetPanels(
    screen: OneClientPauseScreen,
    widgets: PauseWidgetsHandle,
    panels: List<PauseWidget>,
    width: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    if (panels.isEmpty()) return
    val context = remember(screen) { PauseScreenWidgetContext(screen) }
    val drag = rememberWidgetDrag(Orientation.Vertical, panels, widgets.refresh, PauseWidgetRegistry::reorderPanels)
    val shown = drag.shown
    Box(modifier) {
        ContextMenuHost {
            Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(LobbyLayout.Gap)) {
                shown.forEachIndexed { index, widget ->
                    key(widget.id) {
                        val enter = rememberPanelEnter()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxHeight)
                                .widgetGuard(widget.id)
                                .reorderable(drag, widget.id, index)
                                .contextMenu { widgetMenu(widget, shown, true, widgets.refresh, PauseWidgetRegistry::reorderPanels, asPanel = false) }
                                .graphicsLayer {
                                    alpha = enter.value
                                    translationY = (1f - enter.value) * PANEL_RISE.toPx()
                                }
                                .clip(ppShape(PANEL_RADIUS))
                                .pauseGlass(PANEL_RADIUS)
                                .then(if (widget.fullBleed()) Modifier else Modifier.padding(PANEL_PADDING)),
                        ) {
                            WidgetGuard(widget.id) { widget.Panel(context) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPanelEnter(): Animatable<Float, AnimationVector1D> {
    val reduced = Motion.reduced
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) enter.snapTo(1f) else enter.animateTo(1f, tween(PANEL_ENTER_MILLIS))
    }
    return enter
}
