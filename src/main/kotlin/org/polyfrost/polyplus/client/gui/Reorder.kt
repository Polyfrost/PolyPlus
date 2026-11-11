package org.polyfrost.polyplus.client.gui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import kotlin.math.abs

private const val SCROLL_LIFT_THRESHOLD = 1f

internal fun AwaitPointerEventScope.pastTouchSlop(from: Offset, to: Offset): Boolean =
    (to - from).getDistance() > viewConfiguration.touchSlop

internal suspend fun AwaitPointerEventScope.awaitReorderSlop(
    pointer: PointerId,
    origin: Offset,
    pass: PointerEventPass,
    liftOnScroll: Boolean = false,
): Offset? {
    var scrolled = 0f
    while (true) {
        val event = awaitPointerEvent(pass)
        val change = event.changes.firstOrNull { it.id == pointer } ?: return null
        if (!change.pressed) return null
        val at = change.position
        val scroll = event.type == PointerEventType.Scroll
        if (scroll) scrolled += abs(change.scrollDelta.y)
        if ((liftOnScroll && scrolled >= SCROLL_LIFT_THRESHOLD) || pastTouchSlop(origin, at)) {
            if (!scroll) change.consume()
            return at
        }
    }
}

internal suspend fun AwaitPointerEventScope.followReorderDrag(
    pointer: PointerId,
    from: Offset,
    pass: PointerEventPass,
    onMove: (delta: Offset, at: Offset, scroll: Boolean) -> Unit,
) {
    var last = from
    while (true) {
        val event = awaitPointerEvent(pass)
        val change = event.changes.firstOrNull { it.id == pointer } ?: return
        val scroll = event.type == PointerEventType.Scroll
        if (!scroll) change.consume()
        if (!change.pressed) return
        val at = change.position
        onMove(at - last, at, scroll)
        last = at
    }
}
