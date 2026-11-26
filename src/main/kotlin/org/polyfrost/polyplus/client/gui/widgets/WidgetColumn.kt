@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSeparator
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleControl
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.components.UiSound
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.playUiSound
import org.polyfrost.oneconfig.api.ui.v1.components.withItemMoved
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.KitDangerColor
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.pausescreen.asset
import kotlin.math.max
import kotlin.math.roundToInt

private val FRAME_RADIUS = 16.dp
private val FRAME_PADDING = 12.dp
private val FRAME_GAP = 8.dp
private val EDITOR_GAP = 4.dp
private val EDITOR_RADIUS = 8.dp
private val HANDLE_SIZE = 24.dp
private val HANDLE_GLYPH = 14.dp
private val EDITOR_GLYPH = 16.dp
private val LIFT_ELEVATION = 14.dp
private const val HANDLE_ICON = "/assets/oneconfig/ico/dots-grid.svg"

private class ReorderList(private val scope: CoroutineScope) {
    var base: List<String> = emptyList()
    var commit: (List<String>) -> Unit = {}

    private var override by mutableStateOf<List<String>?>(null)
    var dragging by mutableStateOf<String?>(null)
        private set
    private var settling by mutableStateOf<String?>(null)

    private var offset by mutableFloatStateOf(0f)
    private var startTop = 0f
    private var gap = 0f
    private val heights = HashMap<String, Float>()
    private val targets = HashMap<String, Float>()
    private val slides = HashMap<String, Animatable<Float, AnimationVector1D>>()
    private var lastOrder: List<String> = emptyList()

    val shown: List<String> get() = override ?: base

    fun lifted(id: String): Boolean = id == dragging || id == settling

    fun arrange(ids: List<String>, sizes: List<Float>, gapPx: Float): List<Float> {
        gap = gapPx
        val live = ids.toSet()
        slides.keys.retainAll(live)
        targets.keys.retainAll(live)
        heights.keys.retainAll(live)
        ids.forEachIndexed { i, id -> heights[id] = sizes[i] }
        val known = shown.filter { it in live }
        val placed = known.toSet()
        val order = known + ids.filter { it !in placed }
        val slots = slotsOf(order)
        val held = dragging
        val reordered = order != lastOrder && order.size == lastOrder.size && order.toSet() == lastOrder.toSet()
        val animate = held != null || settling != null || reordered
        lastOrder = order
        return ids.map { id ->
            val slot = slots[id] ?: 0f
            val slide = slides[id]
            if (slide == null) {
                slides[id] = Animatable(slot)
                targets[id] = slot
            } else if (targets.put(id, slot) != slot && id != held) {
                if (animate) scope.launch { glide(slide, slot) } else slides[id] = Animatable(slot)
            }
            slot
        }
    }

    fun y(id: String, slot: Float): Float = if (id == dragging) dragTop() else slides[id]?.value ?: slot

    fun start(id: String): Boolean {
        if (dragging != null) return false
        val slide = slides[id] ?: return false
        startTop = slide.value
        offset = 0f
        override = shown
        settling = null
        dragging = id
        playUiSound(UiSound.HudDragStart)
        return true
    }

    fun move(dy: Float) {
        offset += dy
        val id = dragging ?: return
        repeat(shown.size) {
            val ids = shown
            val index = ids.indexOf(id)
            if (index < 0) return
            val slots = slotsOf(ids)
            val top = dragTop()
            val bottom = top + (heights[id] ?: 0f)
            val next = ids.getOrNull(index + 1)
            val prev = ids.getOrNull(index - 1)
            val target = when {
                next != null && bottom > centre(slots, next) -> index + 1
                prev != null && top < centre(slots, prev) -> index - 1
                else -> return
            }
            override = ids.withItemMoved(index, target) ?: return
        }
    }

    fun end() {
        val id = dragging ?: return
        val order = override ?: base
        val from = dragTop()
        val slide = slides[id]
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            slide?.snapTo(from)
            settling = id
            dragging = null
            if (order != base) commit(order)
            override = null
            playUiSound(UiSound.HudDragEnd)
            try {
                val slot = slotsOf(order)[id] ?: return@launch
                targets[id] = slot
                if (slide != null) glide(slide, slot)
            } finally {
                if (settling == id) settling = null
            }
        }
    }

    private suspend fun glide(slide: Animatable<Float, AnimationVector1D>, to: Float) {
        if (Motion.reduced) slide.snapTo(to) else slide.animateTo(to, spring(stiffness = Spring.StiffnessMediumLow))
    }

    private fun slotsOf(ids: List<String>): Map<String, Float> {
        val slots = HashMap<String, Float>(ids.size)
        var y = 0f
        ids.forEach { id ->
            slots[id] = y
            y += (heights[id] ?: 0f) + gap
        }
        return slots
    }

    private fun centre(slots: Map<String, Float>, id: String): Float = (slots[id] ?: 0f) + (heights[id] ?: 0f) / 2f

    private fun total(): Float = heights.values.sum() + gap * max(0, heights.size - 1)

    private fun dragTop(): Float {
        val height = heights[dragging] ?: 0f
        return (startTop + offset).coerceIn(0f, max(0f, total() - height))
    }
}

@Composable
private fun rememberReorderList(ids: List<String>, commit: (List<String>) -> Unit): ReorderList {
    val scope = rememberCoroutineScope()
    val list = remember { ReorderList(scope) }
    list.base = ids
    list.commit = commit
    return list
}

@Composable
private fun ReorderLayout(list: ReorderList, gap: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val child = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(child) }
        val ids = measurables.mapIndexed { i, it -> it.layoutId as? String ?: "#$i" }
        val slots = list.arrange(ids, placeables.map { it.height.toFloat() }, gapPx.toFloat())
        val height = (placeables.sumOf { it.height } + gapPx * (placeables.size - 1)).coerceAtLeast(0)
        val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
        layout(width, constraints.constrainHeight(height)) {
            placeables.forEachIndexed { i, placeable ->
                val id = ids[i]
                placeable.place(0, list.y(id, slots[i]).roundToInt(), if (list.lifted(id)) 1f else 0f)
            }
        }
    }
}

private fun Modifier.dragSource(list: ReorderList, id: String): Modifier = this
    .pointerInput(list, id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val crossed = awaitSlop(down.id, down.position) ?: return@awaitEachGesture
            if (!list.start(id)) return@awaitEachGesture
            try {
                list.move(crossed.y - down.position.y)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.type == PointerEventType.Scroll) continue
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val dy = change.position.y - change.previousPosition.y
                    change.consume()
                    if (dy != 0f) list.move(dy)
                }
            } finally {
                list.end()
            }
        }
    }

private suspend fun AwaitPointerEventScope.awaitSlop(pointer: PointerId, origin: Offset): Offset? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointer } ?: return null
        if (event.type == PointerEventType.Scroll) continue
        if (!change.pressed || change.isConsumed) return null
        if ((change.position - origin).getDistance() > viewConfiguration.touchSlop) {
            change.consume()
            return change.position
        }
    }
}

@Composable
private fun Modifier.lift(list: ReorderList, id: String, radius: Dp): Modifier {
    val theme = OneConfigTheme.current
    val elevation by animateDpAsState(if (list.lifted(id)) LIFT_ELEVATION else 0.dp)
    if (!theme.shadowEnabled || elevation <= 0.dp) return this
    return shadow(elevation, OneConfigTheme.shape(radius), clip = false, ambientColor = theme.shadowColor, spotColor = theme.shadowColor)
}

private fun widgetMenu(surface: WidgetSurface, id: String, shown: List<String>, scope: WidgetScope): List<MenuEntry> = buildList {
    val index = shown.indexOf(id)
    shown.withItemMoved(index, index - 1)?.let { ids ->
        add(MenuAction("Move up", { ShellWidgetRegistry.reorderVisible(surface, ids) }, icon = asset("chevron-up")))
    }
    shown.withItemMoved(index, index + 1)?.let { ids ->
        add(MenuAction("Move down", { ShellWidgetRegistry.reorderVisible(surface, ids) }))
    }
    add(MenuAction("Remove", { ShellWidgetRegistry.remove(surface, id) }, icon = asset("trash-01"), danger = true, dangerColor = KitDangerColor))
    add(MenuSeparator)
    add(MenuAction("Edit widgets…", { scope.openCustomize() }, icon = asset("edit-02")))
}

@Composable
fun WidgetFrame(
    widget: ShellWidget,
    glass: @Composable Modifier.(Dp) -> Modifier,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = OneConfigTheme.shape(FRAME_RADIUS)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = widget.size.height)
            .clip(shape)
            .glass(FRAME_RADIUS)
            .then(if (widget.bleed) Modifier else Modifier.padding(FRAME_PADDING)),
        contentAlignment = Alignment.CenterStart,
        content = content,
    )
}

@Composable
fun WidgetColumn(
    surface: WidgetSurface,
    width: Dp,
    maxHeight: Dp,
    glass: @Composable Modifier.(Dp) -> Modifier,
    scope: WidgetScope,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    itemWrapper: (@Composable (widget: ShellWidget, layoutModifier: Modifier, content: @Composable (Modifier) -> Unit) -> Unit)? = null,
) {
    val widgets = rememberShellWidgets(surface)
    if (widgets.isEmpty()) return
    val byId = widgets.associateBy { it.id }
    val list = rememberReorderList(widgets.map { it.id }) { ShellWidgetRegistry.reorderVisible(surface, it) }
    ReorderLayout(
        list,
        FRAME_GAP,
        // no scroll container (it clips) while the menu editor moves or scales widgets out of the column
        if (scroll) modifier.width(width).heightIn(max = maxHeight).verticalScroll(rememberScrollState()) else modifier.width(width),
    ) {
        widgets.forEach { widget ->
            key(widget.id) {
                val frame: @Composable (Modifier) -> Unit = { outer ->
                    WidgetFrame(
                        widget,
                        glass,
                        outer
                            .lift(list, widget.id, FRAME_RADIUS)
                            .shellWidgetGuard(widget.id)
                            .dragSource(list, widget.id)
                            .contextMenu(entries = { widgetMenu(surface, widget.id, list.shown.filter { it in byId }, scope) }),
                    ) {
                        ShellWidgetGuarded(widget.id) { widget.Content(scope) }
                    }
                }
                if (itemWrapper == null) frame(Modifier.layoutId(widget.id)) else itemWrapper(widget, Modifier.layoutId(widget.id), frame)
            }
        }
    }
}

@Composable
fun WidgetListEditor(surface: WidgetSurface, modifier: Modifier = Modifier) {
    val revision = ShellWidgetRegistry.revision
    val available = remember(surface, revision) { ShellWidgetRegistry.arranged(surface) }
    val enabled = remember(surface, revision) { ShellWidgetRegistry.ids(surface).toSet() }
    val list = rememberReorderList(available.map { it.id }) { ShellWidgetRegistry.reorderVisible(surface, it) }
    ReorderLayout(list, EDITOR_GAP, modifier.fillMaxWidth()) {
        available.forEach { widget ->
            key(widget.id) { EditorRow(surface, widget, widget.id in enabled, list, Modifier.layoutId(widget.id)) }
        }
    }
}

@Composable
private fun EditorRow(surface: WidgetSurface, widget: ShellWidget, checked: Boolean, list: ReorderList, modifier: Modifier) {
    val theme = OneConfigTheme.current
    val muted = theme.mutedText
    val lifted = list.lifted(widget.id)
    Row(
        modifier
            .lift(list, widget.id, EDITOR_RADIUS)
            .then(if (lifted) Modifier.background(theme.componentBackground, OneConfigTheme.shape(EDITOR_RADIUS)) else Modifier)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(HANDLE_SIZE)
                .dragSource(list, widget.id),
            contentAlignment = Alignment.Center,
        ) {
            ThemedIcon(HANDLE_ICON, muted, HANDLE_GLYPH)
        }
        if (widget.icon.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 6.dp).size(EDITOR_GLYPH), contentAlignment = Alignment.Center) {
                ThemedIcon(widget.icon, muted, EDITOR_GLYPH)
            }
        }
        ToggleRow(
            widget.title,
            checked,
            { ShellWidgetRegistry.set(surface, widget.id, it) },
            Modifier.weight(1f),
            control = ToggleControl.Switch,
        )
    }
}
