@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.ContextItem
import org.polyfrost.polyplus.client.gui.ContextMenuHost
import org.polyfrost.polyplus.client.gui.ContextMenuList
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.contextMenu
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.lobbyshell.EscapeCloses
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

private class CoordinatesRef {
    var coordinates: LayoutCoordinates? = null

    fun window(local: Offset): Offset? = coordinates?.takeIf { it.isAttached }?.localToWindow(local)
}

private class WidgetDrag(val vertical: Boolean, private val gap: Float, private val commit: (List<String>) -> Unit) {
    var order by mutableStateOf<List<PauseWidget>>(emptyList())
    var dragging by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)
    val sizes = HashMap<String, Int>()

    fun shown(added: List<PauseWidget>): List<PauseWidget> = if (dragging != null) order else added

    fun start(id: String, current: List<PauseWidget>) {
        order = current
        offset = 0f
        dragging = id
    }

    fun move(delta: Float) {
        val id = dragging ?: return
        offset += delta
        val index = order.indexOfFirst { it.id == id }
        if (index < 0) return
        if (offset > 0f && index < order.lastIndex) {
            val span = (sizes[order[index + 1].id] ?: return) + gap
            if (offset > span / 2f) {
                order = order.swapped(index, index + 1)
                offset -= span
            }
        } else if (offset < 0f && index > 0) {
            val span = (sizes[order[index - 1].id] ?: return) + gap
            if (offset < -span / 2f) {
                order = order.swapped(index, index - 1)
                offset += span
            }
        }
    }

    fun end() {
        if (dragging == null) return
        val ids = order.map { it.id }
        dragging = null
        offset = 0f
        commit(ids)
    }
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().also {
    val held = it[a]
    it[a] = it[b]
    it[b] = held
}

private fun List<PauseWidget>.moved(id: String, by: Int): List<String>? {
    val ids = map { it.id }.toMutableList()
    val from = ids.indexOf(id)
    val to = from + by
    if (from < 0 || to !in ids.indices) return null
    ids.add(to, ids.removeAt(from))
    return ids
}

@Composable
private fun rememberWidgetDrag(
    vertical: Boolean,
    gap: Dp,
    refresh: () -> Unit,
    commit: (List<String>) -> Unit = PauseWidgetRegistry::reorder,
): WidgetDrag {
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val latest by rememberUpdatedState(refresh)
    val store by rememberUpdatedState(commit)
    return remember(vertical, gapPx) {
        WidgetDrag(vertical, gapPx) { ids ->
            store(ids)
            latest()
        }
    }
}

@Composable
private fun Modifier.reorderable(
    drag: WidgetDrag,
    id: String,
    current: List<PauseWidget>,
    pass: PointerEventPass = PointerEventPass.Initial,
): Modifier {
    val ref = remember { CoordinatesRef() }
    val list by rememberUpdatedState(current)
    val lifted = drag.dragging == id
    return this
        .onSizeChanged { drag.sizes[id] = if (drag.vertical) it.height else it.width }
        .onGloballyPositioned { ref.coordinates = it }
        .pointerInput(drag, id, pass) {
            awaitPointerEventScope {
                while (true) {
                    val press = awaitPointerEvent(pass)
                    if (press.type != PointerEventType.Press || !press.buttons.isPrimaryPressed) continue
                    val pointer = press.changes.firstOrNull() ?: continue
                    val origin = ref.window(pointer.position) ?: continue
                    var last = origin
                    var moving = false
                    var claimed = false
                    while (true) {
                        val event = awaitPointerEvent(pass)
                        val change = event.changes.firstOrNull { it.id == pointer.id } ?: break
                        if (!change.pressed) {
                            if (moving) change.consume()
                            break
                        }
                        val at = ref.window(change.position) ?: break
                        if (!moving && change.isConsumed) claimed = true
                        if (!moving && !claimed && (at - origin).getDistance() > viewConfiguration.touchSlop) {
                            moving = true
                            drag.start(id, list)
                        }
                        if (moving) {
                            drag.move(if (drag.vertical) at.y - last.y else at.x - last.x)
                            change.consume()
                        }
                        last = at
                    }
                    if (moving && drag.dragging == id) drag.end()
                }
            }
        }
        .zIndex(if (lifted) 1f else 0f)
        .graphicsLayer {
            if (lifted) {
                if (drag.vertical) translationY = drag.offset else translationX = drag.offset
                alpha = LIFTED_ALPHA
            }
        }
}

private fun widgetMenu(
    widget: PauseWidget,
    current: List<PauseWidget>,
    vertical: Boolean,
    refresh: () -> Unit,
    commit: (List<String>) -> Unit = PauseWidgetRegistry::reorder,
    asPanel: Boolean? = null,
): List<ContextItem> = buildList {
    val reorder: (List<String>?) -> Unit = { ids ->
        if (ids != null) {
            commit(ids)
            refresh()
        }
    }
    if (asPanel != null) {
        add(ContextItem(if (asPanel) "Show as panel" else "Show in bar") {
            PauseWidgetRegistry.setPanel(widget.id, asPanel)
            refresh()
        })
    }
    current.moved(widget.id, -1)?.let { ids ->
        add(ContextItem(if (vertical) "Move up" else "Move left", asset(if (vertical) "chevron-up" else "chevron-left")) { reorder(ids) })
    }
    current.moved(widget.id, 1)?.let { ids ->
        add(ContextItem(if (vertical) "Move down" else "Move right", if (vertical) null else asset("chevron-right")) { reorder(ids) })
    }
    add(ContextItem("Remove widget", asset("trash-01"), danger = true) {
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
                ContextMenuList(
                    addable.map { widget ->
                        ContextItem(runCatching { widget.title }.getOrDefault(widget.id), runCatching { widget.icon }.getOrNull()) {
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
    val drag = rememberWidgetDrag(vertical = false, gap = 0.dp, refresh = widgets.refresh)
    val added = drag.shown(view.added.filter { it.id !in panelled })
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
                                .reorderable(drag, widget.id, added)
                                .contextMenu { widgetMenu(widget, added, false, widgets.refresh, asPanel = barPanelToggle(widget, panelModes, panelArea)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (index > 0 || view.builtIns.isNotEmpty()) BarRule()
                            widget.Bar(context)
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
    val drag = rememberWidgetDrag(vertical = true, gap = CARD_GAP, refresh = widgets.refresh)
    val added = drag.shown(view.added)
    Box(modifier) {
        ContextMenuHost {
            Column(
                Modifier.width(width).pauseGlass(16.dp).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
            ) {
                view.builtIns.forEach { widget ->
                    key(widget.id) { widget.Card(context) }
                }
                added.forEach { widget ->
                    key(widget.id) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .reorderable(drag, widget.id, added)
                                .contextMenu { widgetMenu(widget, added, true, widgets.refresh) },
                        ) {
                            widget.Card(context)
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
    val drag = rememberWidgetDrag(vertical = true, gap = LobbyLayout.Gap, refresh = widgets.refresh, commit = PauseWidgetRegistry::reorderPanels)
    val shown = drag.shown(panels)
    Box(modifier) {
        ContextMenuHost {
            Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(LobbyLayout.Gap)) {
                shown.forEach { widget ->
                    key(widget.id) {
                        val enter = rememberPanelEnter()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxHeight)
                                .reorderable(drag, widget.id, shown, PointerEventPass.Main)
                                .contextMenu { widgetMenu(widget, shown, true, widgets.refresh, PauseWidgetRegistry::reorderPanels, asPanel = false) }
                                .graphicsLayer {
                                    alpha = enter.value
                                    translationY = (1f - enter.value) * PANEL_RISE.toPx()
                                }
                                .clip(ppShape(PANEL_RADIUS))
                                .pauseGlass(PANEL_RADIUS)
                                .then(if (widget is FullBleedPanel) Modifier else Modifier.padding(PANEL_PADDING)),
                        ) {
                            widget.Panel(context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPanelEnter(): Animatable<Float, AnimationVector1D> {
    val reduced = PolyPlusConfig.reducedMotion
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) enter.snapTo(1f) else enter.animateTo(1f, tween(PANEL_ENTER_MILLIS))
    }
    return enter
}
