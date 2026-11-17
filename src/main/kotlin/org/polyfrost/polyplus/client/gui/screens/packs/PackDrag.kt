@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.packs

//? if >= 26.3 || = 1.8.9 {
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
//?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

//? if >= 26.3 || = 1.8.9 {
internal class PackDragPayload(
    val from: PackSide,
    val ids: List<String>,
    val item: PackItem?,
    val folder: PackFolder?,
    val sources: Set<String>,
) {
    val count: Int get() = ids.size
}

internal sealed interface PackDropTarget {
    class Column(val side: PackSide, val anchor: String?) : PackDropTarget

    class Folder(val path: String) : PackDropTarget
}

internal class PackLanding(
    val ids: List<String>,
    val item: PackItem?,
    val folder: PackFolder?,
    val count: Int,
    val from: Offset,
    val into: String?,
)

private class PackRowSpot(val rect: Rect, val fixed: Boolean)

internal class PackDragState {
    private val columns = HashMap<PackSide, Rect>()
    private val rows = HashMap<PackSide, LinkedHashMap<String, PackRowSpot>>()
    private val folders = HashMap<String, Pair<String, Rect>>()
    private val scrolls = HashMap<PackSide, ScrollState>()

    var payload by mutableStateOf<PackDragPayload?>(null)
        private set

    var pointer by mutableStateOf(Offset.Zero)
        private set

    var target by mutableStateOf<PackDropTarget?>(null)
        private set

    var landing by mutableStateOf<PackLanding?>(null)
        private set

    var grab = Offset.Zero
        private set

    var cardSize by mutableStateOf(IntSize.Zero)
        private set

    var folderDrops = false

    var spacing = 0f

    val active: Boolean get() = payload != null

    val cardTopLeft: Offset get() = pointer - grab

    fun hidden(id: String): Boolean = payload?.ids?.contains(id) == true

    fun landingHides(id: String): Boolean = landing?.ids?.contains(id) == true

    fun gapBefore(side: PackSide, anchor: String?): Boolean {
        val column = target as? PackDropTarget.Column ?: return false
        return column.side == side && side == PackSide.Selected && column.anchor == anchor && payload != null
    }

    fun folderTargeted(path: String): Boolean = (target as? PackDropTarget.Folder)?.path == path

    fun columnTargeted(side: PackSide): Boolean {
        val column = target as? PackDropTarget.Column ?: return false
        return column.side == side && payload?.from != side
    }

    fun column(side: PackSide, coordinates: LayoutCoordinates, scroll: ScrollState?) {
        columns[side] = coordinates.boundsInRoot()
        if (scroll != null) scrolls[side] = scroll
    }

    fun row(side: PackSide, id: String, fixed: Boolean, coordinates: LayoutCoordinates) {
        if (hidden(id)) {
            rows[side]?.remove(id)
            return
        }
        rows.getOrPut(side) { LinkedHashMap() }[id] = PackRowSpot(rootRect(coordinates), fixed)
    }

    fun forget(side: PackSide, id: String) {
        rows[side]?.remove(id)
    }

    fun folderSpot(key: String, path: String, coordinates: LayoutCoordinates) {
        folders[key] = path to rootRect(coordinates)
    }

    fun forgetFolder(key: String) {
        folders.remove(key)
    }

    fun rowRect(id: String, prefer: PackSide?): Rect? {
        prefer?.let { side -> rows[side]?.get(id)?.let { return it.rect } }
        return rows.values.firstNotNullOfOrNull { it[id]?.rect }
    }

    fun onScreen(rect: Rect): Boolean = columns.values.any { it.contains(rect.center) }

    fun folderRect(path: String): Rect? = folders.values.firstOrNull { it.first == path }?.second

    fun start(payload: PackDragPayload, at: Offset, card: Rect) {
        landing = null
        grab = at - card.topLeft
        cardSize = IntSize(card.width.toInt(), card.height.toInt())
        this.payload = payload
        rows.values.forEach { spots -> payload.ids.forEach(spots::remove) }
        move(at)
    }

    fun move(at: Offset) {
        pointer = at
        target = resolve(at)
    }

    fun finish(): Pair<PackDragPayload, PackDropTarget?>? {
        val drag = payload ?: return null
        val drop = target
        val from = cardTopLeft
        payload = null
        target = null
        landing = PackLanding(drag.ids, drag.item, drag.folder, drag.count, from, (drop as? PackDropTarget.Folder)?.path)
        return drag to drop
    }

    fun cancel() {
        val drag = payload ?: return
        val from = cardTopLeft
        payload = null
        target = null
        landing = PackLanding(drag.ids, drag.item, drag.folder, drag.count, from, null)
    }

    fun landed(landing: PackLanding) {
        if (this.landing === landing) this.landing = null
    }

    fun edgeScroll(): Pair<ScrollState, Float>? {
        if (!active) return null
        for ((side, rect) in columns) {
            if (pointer.x !in rect.left..rect.right) continue
            val scroll = scrolls[side] ?: continue
            val band = minOf(EDGE, rect.height / 4f)
            if (pointer.y in rect.top..(rect.top + band)) return scroll to -STEP
            if (pointer.y in (rect.bottom - band)..rect.bottom) return scroll to STEP
        }
        return null
    }

    private fun resolve(at: Offset): PackDropTarget? {
        val drag = payload ?: return null
        if (folderDrops && drag.folder == null) {
            val folder = folders.values.firstOrNull { it.second.contains(at) }?.first
            if (folder != null) return if (drag.sources.all { it == folder }) null else PackDropTarget.Folder(folder)
        }
        val side = columns.entries.firstOrNull { it.value.contains(at) }?.key ?: return null
        if (side == PackSide.Available) return if (drag.from == PackSide.Selected) PackDropTarget.Column(side, null) else null
        return PackDropTarget.Column(side, anchor(side, at.y, drag))
    }

    private fun anchor(side: PackSide, y: Float, drag: PackDragPayload): String? {
        val list = rows[side].orEmpty().entries.filter { it.key !in drag.ids }.sortedBy { it.value.rect.top }
        val current = target as? PackDropTarget.Column
        val gapAt = when {
            current == null || current.side != side -> -1
            current.anchor == null -> list.size
            else -> list.indexOfFirst { it.key == current.anchor }
        }
        val shift = cardSize.height + spacing
        var index = 0
        for ((i, entry) in list.withIndex()) {
            val rect = entry.value.rect
            val top = rect.top - if (gapAt in 0..i) shift else 0f
            if (y > top + rect.height / 2f) index = i + 1
        }
        val lead = list.takeWhile { it.value.fixed }.size
        val trail = list.size - list.takeLastWhile { it.value.fixed }.size
        index = index.coerceIn(lead, maxOf(lead, trail))
        return list.getOrNull(index)?.key
    }

    private fun rootRect(coordinates: LayoutCoordinates): Rect {
        val start = coordinates.localToRoot(Offset.Zero)
        val end = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        return Rect(start, end)
    }

    private companion object {
        const val EDGE = 36f
        const val STEP = 10f
    }
}

internal class PackClick(val toggle: Boolean, val range: Boolean, val double: Boolean)

internal fun Modifier.packGesture(
    drag: PackDragState,
    key: Any,
    payload: () -> PackDragPayload?,
    onClick: (PackClick) -> Unit,
    onDrop: () -> Unit,
): Modifier = composed {
    val holder = remember { GestureHolder() }
    val latestPayload by rememberUpdatedState(payload)
    val latestClick by rememberUpdatedState(onClick)
    val latestDrop by rememberUpdatedState(onDrop)
    this
        .onGloballyPositioned { holder.coordinates = it }
        .pointerInput(drag, key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                if (down.isConsumed || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                val modifiers = currentEvent.keyboardModifiers
                var dragging = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.type == PointerEventType.Scroll) continue
                        if (!change.pressed) {
                            if (dragging) {
                                change.consume()
                                dragging = false
                                latestDrop()
                            } else if (!change.isConsumed) {
                                val now = System.currentTimeMillis()
                                val double = now - holder.lastClick < DOUBLE_CLICK_MS
                                holder.lastClick = if (double) 0L else now
                                latestClick(PackClick(modifiers.toggles(), modifiers.isShiftPressed, double))
                            }
                            break
                        }
                        val coordinates = holder.coordinates?.takeIf { it.isAttached } ?: continue
                        val root = coordinates.localToRoot(change.position)
                        if (!dragging) {
                            if ((change.position - down.position).getDistance() <= DRAG_THRESHOLD.toPx()) continue
                            val started = latestPayload() ?: break
                            dragging = true
                            val card = Rect(coordinates.localToRoot(Offset.Zero), coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())))
                            drag.start(started, coordinates.localToRoot(down.position), card)
                            drag.move(root)
                        } else {
                            drag.move(root)
                        }
                        change.consume()
                    }
                } finally {
                    if (dragging && drag.active) drag.cancel()
                }
            }
        }
}

internal fun Modifier.collapsedWhen(collapsed: Boolean): Modifier =
    if (!collapsed) this else layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, 0) { placeable.place(0, 0) }
    }

private class PlacementHolder {
    var anim: Animatable<IntOffset, AnimationVector2D>? = null
}

internal fun Modifier.animatePlacement(snap: Boolean): Modifier = composed {
    val scope = rememberCoroutineScope()
    val holder = remember { PlacementHolder() }
    var target by remember { mutableStateOf(IntOffset.Zero) }
    val latestSnap by rememberUpdatedState(snap)
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val anim = holder.anim ?: Animatable(target, IntOffset.VectorConverter).also { holder.anim = it }
            if (anim.targetValue != target) {
                val to = target
                if (latestSnap || Motion.reduced) {
                    scope.launch { anim.snapTo(to) }
                } else {
                    scope.launch { anim.animateTo(to, spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)) }
                }
            }
            if (latestSnap || Motion.reduced) IntOffset.Zero else anim.value - target
        }
}

private class GestureHolder {
    var coordinates: LayoutCoordinates? = null
    var lastClick = 0L
}

private fun PointerKeyboardModifiers.toggles(): Boolean = isCtrlPressed || isMetaPressed

private const val DOUBLE_CLICK_MS = 350L

private val DRAG_THRESHOLD = 4.dp
//?}
