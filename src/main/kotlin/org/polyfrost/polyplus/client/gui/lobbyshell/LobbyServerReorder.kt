@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ReorderState
import org.polyfrost.oneconfig.api.ui.v1.components.withItemMoved
import org.polyfrost.polyplus.client.gui.awaitReorderSlop
import org.polyfrost.polyplus.client.gui.followReorderDrag
import org.polyfrost.polyplus.client.gui.pastTouchSlop
import kotlin.math.abs

private val AutoScrollZone = 40.dp
private val AutoScrollRamp = 240.dp
private val AutoScrollMaxSpeed = 2400.dp
private const val LIFT_SCALE = 1.04f

private val SettleSpec = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = Offset(0.5f, 0.5f),
)

internal class ReorderSlot(val key: Any, val index: Int, val offset: Offset, val size: IntSize) {
    val bounds: Rect get() = Rect(offset, size.toSize())
}

internal interface ReorderViewport {
    fun slots(): List<ReorderSlot>
    val viewportStart: Int
    val viewportEnd: Int
    val firstIndex: Int
    val firstOffset: Int
    fun keepScroll(index: Int, offset: Int)
    fun scrollBy(delta: Float)
}

internal class GridReorderViewport(private val state: LazyGridState) : ReorderViewport {
    override fun slots(): List<ReorderSlot> =
        state.layoutInfo.visibleItemsInfo.map { ReorderSlot(it.key, it.index, it.offset.toOffset(), it.size) }

    override val viewportStart: Int get() = state.layoutInfo.viewportStartOffset
    override val viewportEnd: Int get() = state.layoutInfo.viewportEndOffset
    override val firstIndex: Int get() = state.firstVisibleItemIndex
    override val firstOffset: Int get() = state.firstVisibleItemScrollOffset
    override fun keepScroll(index: Int, offset: Int) = state.requestScrollToItem(index, offset)
    override fun scrollBy(delta: Float) {
        state.dispatchRawDelta(delta)
    }
}

private class EdgePointer {
    var y: Float? = null
    var height = 0f
}

@Composable
internal fun Modifier.reorderAutoScroll(reorder: ReorderState, list: LazyListState): Modifier {
    val pointer = remember { EdgePointer() }
    val density = LocalDensity.current
    val dragging = reorder.isDragging
    LaunchedEffect(dragging, list, density) {
        if (!dragging) return@LaunchedEffect
        val origin = pointer.y ?: return@LaunchedEffect
        val zone = with(density) { AutoScrollZone.toPx() }
        val rampDistance = with(density) { AutoScrollRamp.toPx() }
        val maxSpeed = with(density) { AutoScrollMaxSpeed.toPx() }
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - lastFrame) / 1e9f).coerceAtMost(1 / 30f)
            lastFrame = frame
            val y = pointer.y ?: continue
            val intoTop = minOf(zone, origin) - y
            val intoBottom = y - maxOf(pointer.height - zone, origin)
            val depth = when {
                intoTop > 0f && list.canScrollBackward -> -intoTop
                intoBottom > 0f && list.canScrollForward -> intoBottom
                else -> 0f
            }
            val ramp = (depth / rampDistance).coerceIn(-1f, 1f)
            if (depth != 0f) list.dispatchRawDelta(ramp * abs(ramp) * maxSpeed * seconds)
        }
    }
    return this
        .onSizeChanged { pointer.height = it.height.toFloat() }
        .pointerInput(pointer) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()?.let { pointer.y = it.position.y }
                }
            }
        }
}

internal class ServerReorderState(
    private val viewport: ReorderViewport,
    private val scope: CoroutineScope,
    private val source: () -> List<ServerEntry>,
    private val enabled: () -> Boolean,
    private val commit: (entry: ServerEntry, savedTarget: Int) -> Unit,
    internal val onClick: (ServerEntry) -> Unit,
    private val density: () -> Density,
) {
    private var dragOrder by mutableStateOf<List<ServerEntry>?>(null)

    val entries: List<ServerEntry> get() = dragOrder ?: source()

    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var overlayKey by mutableStateOf<Any?>(null)
        private set
    var overlaySize by mutableStateOf(IntSize.Zero)
        private set

    private var draggingIndex by mutableIntStateOf(-1)
    private var initialOffset by mutableStateOf(Offset.Zero)
    private var dragDelta by mutableStateOf(Offset.Zero)
    private val settleOffset = Animatable(Offset.Zero, Offset.VectorConverter)
    private var settling by mutableStateOf(false)

    private var slot = Offset.Zero
    private var referenceKey: Any? = null
    private var referenceOffset = Offset.Zero
    private var settle: Job? = null
    private var dragLoop: Job? = null

    internal var containerCoordinates: LayoutCoordinates? = null
    internal val itemCoordinates = mutableMapOf<Any, LayoutCoordinates>()

    val overlayEntry: ServerEntry? get() = overlayKey?.let { key -> entries.firstOrNull { it.key == key } }

    val overlayOffset: Offset
        get() = if (settling) trackSlot() + settleOffset.value else initialOffset + dragDelta

    private fun dragBounds(index: Int): IntRange? {
        if (!enabled()) return null
        val list = entries
        if (list.getOrNull(index)?.kind != ServerKind.SAVED) return null
        val first = list.indexOfFirst { it.kind == ServerKind.SAVED }
        val last = list.indexOfLast { it.kind == ServerKind.SAVED }
        return first..last
    }

    private fun trackSlot(): Offset {
        val items = viewport.slots()
        val own = items.firstOrNull { it.key == overlayKey }
        if (own != null) {
            slot = own.offset
        } else {
            items.firstOrNull { it.key == referenceKey }?.let { slot += it.offset - referenceOffset }
        }
        val reference = items.getOrNull(items.size / 2)
        referenceKey = reference?.key
        referenceOffset = reference?.offset ?: Offset.Zero
        return slot
    }

    private fun slotAt(index: Int): ReorderSlot? = viewport.slots().firstOrNull { it.index == index }

    private fun drawnBounds(info: ReorderSlot): Rect {
        val container = containerCoordinates ?: return info.bounds
        val item = itemCoordinates[info.key] ?: return info.bounds
        if (!item.isAttached || !container.isAttached) return info.bounds
        return container.localBoundingBoxOf(item, clipBounds = false)
    }

    internal fun isOverSettlingItem(position: Offset): Boolean =
        settling && Rect(overlayOffset, overlaySize.toSize()).contains(position)

    internal fun settlingEntry(): ServerEntry? = if (settling) overlayEntry else null

    internal fun draggableKeyAt(position: Offset): Any? {
        if (!enabled()) return null
        if (isOverSettlingItem(position)) return overlayKey
        itemCoordinates.values.removeAll { !it.isAttached }
        val info = viewport.slots().lastOrNull { drawnBounds(it).contains(position) } ?: return null
        return info.key.takeIf { dragBounds(info.index) != null }
    }

    internal fun onDragStart(key: Any): Boolean {
        val info = viewport.slots().firstOrNull { it.key == key } ?: return false
        if (dragBounds(info.index) == null) return false
        val from = if (settling && overlayKey == key) overlayOffset else drawnBounds(info).topLeft
        settle?.cancel()
        settling = false
        dragOrder = source()
        draggingKey = key
        overlayKey = key
        overlaySize = info.size
        draggingIndex = info.index
        initialOffset = from
        dragDelta = Offset.Zero
        trackSlot()
        dragLoop = scope.launch { dragLoop() }
        return true
    }

    internal fun onDrag(delta: Offset) {
        if (draggingKey != null) dragDelta += delta
    }

    internal fun onDragEnd() {
        val key = draggingKey ?: return
        settleOnHoveredItem()
        val index = draggingIndex
        val fromSlot = overlayOffset - (slotAt(index)?.offset ?: trackSlot())
        val order = dragOrder
        stop()
        dragOrder = null
        order?.getOrNull(index)?.let { entry ->
            commit(entry, order.subList(0, index).count { it.kind == ServerKind.SAVED })
        }
        settling = true
        settle = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffset.snapTo(fromSlot)
            settleOffset.animateTo(Offset.Zero, Motion.spec(SettleSpec))
            settling = false
            if (overlayKey == key) overlayKey = null
        }
    }

    internal fun cancel() {
        if (draggingKey == null) return
        stop()
        dragOrder = null
        overlayKey = null
    }

    private fun stop() {
        dragLoop?.cancel()
        dragLoop = null
        draggingKey = null
        draggingIndex = -1
        dragDelta = Offset.Zero
    }

    private fun settleOnHoveredItem() {
        if (draggingKey == null) return
        val bounds = dragBounds(draggingIndex) ?: return
        val info = slotAt(draggingIndex)
        val dragged = Rect(overlayOffset, overlaySize.toSize())
        val allowed = viewport.slots().filter { it.index in bounds }
        var best: ReorderSlot? = null
        var bestOverlap = info?.let { dragged.overlap(it.bounds) } ?: 0f
        allowed.forEach { other ->
            if (other.index == draggingIndex) return@forEach
            val overlap = dragged.overlap(other.bounds)
            if (overlap > bestOverlap) {
                best = other
                bestOverlap = overlap
            }
        }
        if (best == null && bestOverlap == 0f) {
            fun distance(item: ReorderSlot) = (item.bounds.center - dragged.center).getDistance()
            val ownDistance = info?.let(::distance) ?: Float.POSITIVE_INFINITY
            best = allowed.minByOrNull(::distance)?.takeIf { distance(it) < ownDistance }
        }
        val target = best ?: return
        val first = viewport.firstIndex
        if (first in minOf(draggingIndex, target.index)..maxOf(draggingIndex, target.index)) {
            viewport.keepScroll(first, viewport.firstOffset)
        }
        dragOrder?.withItemMoved(draggingIndex, target.index)?.let { dragOrder = it }
        draggingIndex = target.index
    }

    private suspend fun dragLoop() {
        var lastFrame = withFrameNanos { it }
        while (scope.isActive && draggingKey != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - lastFrame) / 1e9f).coerceAtMost(1 / 30f)
            lastFrame = frame
            trackSlot()
            settleOnHoveredItem()
            val top = overlayOffset.y
            val bottom = top + overlaySize.height
            val start = viewport.viewportStart
            val end = viewport.viewportEnd
            val bounds = dragBounds(draggingIndex)
            val inView = viewport.slots().filter { it.offset.y >= start && it.offset.y + it.size.height <= end }
            val canScrollUp = bounds != null && (inView.isEmpty() || bounds.first < inView.first().index)
            val canScrollDown = bounds != null && (inView.isEmpty() || bounds.last > inView.last().index)
            val (zone, rampDistance, maxSpeed) = with(density()) {
                Triple(AutoScrollZone.toPx(), AutoScrollRamp.toPx(), AutoScrollMaxSpeed.toPx())
            }
            val intoTop = minOf(start + zone, initialOffset.y) - top
            val intoBottom = bottom - maxOf(end - zone, initialOffset.y + overlaySize.height)
            val depth = when {
                intoTop > 0f && canScrollUp -> -intoTop
                intoBottom > 0f && canScrollDown -> intoBottom
                else -> 0f
            }
            val ramp = (depth / rampDistance).coerceIn(-1f, 1f)
            if (depth != 0f) viewport.scrollBy(ramp * abs(ramp) * maxSpeed * seconds)
        }
    }
}

@Composable
internal fun rememberServerReorder(
    lobby: LobbyShell,
    shown: List<ServerEntry>,
    enabled: Boolean,
    viewport: ReorderViewport,
): ServerReorderState {
    val scope = rememberCoroutineScope()
    val currentShown by rememberUpdatedState(shown)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentDensity by rememberUpdatedState(LocalDensity.current)
    val state = remember(viewport, scope) {
        ServerReorderState(
            viewport = viewport,
            scope = scope,
            source = { currentShown },
            enabled = { currentEnabled },
            commit = { entry, target -> lobby.servers.moveTo(entry, target) },
            onClick = { entry -> lobby.select(entry.data) },
            density = { currentDensity },
        )
    }
    LaunchedEffect(state, enabled) { if (!enabled) state.cancel() }
    return state
}

internal fun Modifier.serverReorderContainer(state: ServerReorderState): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        val key = state.draggableKeyAt(down.position) ?: return@awaitEachGesture
        val caught = state.isOverSettlingItem(down.position)
        val caughtEntry = if (caught) state.settlingEntry() else null
        val start = if (caught) {
            down.consume()
            down.position
        } else {
            awaitReorderSlop(down.id, down.position, PointerEventPass.Initial, liftOnScroll = true) ?: return@awaitEachGesture
        }
        if (!state.onDragStart(key)) return@awaitEachGesture
        state.onDrag(start - down.position)
        var click = caught
        try {
            followReorderDrag(down.id, start, PointerEventPass.Initial) { delta, at, scroll ->
                if (scroll || pastTouchSlop(down.position, at)) click = false
                state.onDrag(delta)
            }
        } finally {
            state.onDragEnd()
        }
        if (click) caughtEntry?.let(state.onClick)
    }
}.onPlaced { state.containerCoordinates = it }

internal fun Modifier.serverReorderItem(state: ServerReorderState, key: Any): Modifier = this
    .onPlaced { state.itemCoordinates[key] = it }
    .graphicsLayer { alpha = if (state.overlayKey == key) 0f else 1f }

@Composable
internal fun Modifier.serverReorderOverlay(state: ServerReorderState): Modifier {
    val density = LocalDensity.current
    val width = with(density) { state.overlaySize.width.toDp() }
    val height = with(density) { state.overlaySize.height.toDp() }
    val scale by animateFloatAsState(if (state.draggingKey != null) LIFT_SCALE else 1f, Motion.spec(spring()))
    return this
        .zIndex(1f)
        .offset { state.overlayOffset.round() }
        .size(width, height)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

private fun Rect.overlap(other: Rect): Float {
    val width = minOf(right, other.right) - maxOf(left, other.left)
    val height = minOf(bottom, other.bottom) - maxOf(top, other.top)
    return if (width > 0f && height > 0f) width * height else 0f
}

private fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())

private fun IntSize.toSize() = Size(width.toFloat(), height.toFloat())
