package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeFollow
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeSide
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpritePath
import org.polyfrost.polyplus.nameplate.engine.internal.layout.OrnamentAnchor
import org.polyfrost.polyplus.nameplate.engine.internal.strip.IntList
import kotlin.math.abs
import kotlin.math.sign

internal class EdgeRoute(val xs: IntArray, val ys: IntArray, val facings: IntArray) {
    private val starts = IntArray(xs.size).also { sums ->
        for (i in 1 until xs.size) {
            sums[i] = sums[i - 1] + maxOf(abs(xs[i] - xs[i - 1]), abs(ys[i] - ys[i - 1]))
        }
    }

    val length: Int = starts.last()

    fun segmentAt(along: Int): Int {
        var low = 0
        var high = facings.size - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (starts[middle] <= along) low = middle else high = middle - 1
        }
        return low
    }

    fun x(segment: Int, along: Int): Int = xs[segment] + (xs[segment + 1] - xs[segment]).sign * (along - starts[segment])

    fun y(segment: Int, along: Int): Int = ys[segment] + (ys[segment + 1] - ys[segment]).sign * (along - starts[segment])

    fun nearest(x: Int, y: Int, width: Int, height: Int): Long {
        var best = Long.MAX_VALUE
        var bestY = Int.MAX_VALUE
        var spot = EdgeSpot.pack(xs[0], ys[0])
        for (along in 0..length) {
            val segment = segmentAt(along)
            val stepX = x(segment, along)
            val stepY = y(segment, along)
            val offsetX = 2L * (stepX - x) + width
            val offsetY = 2L * (stepY - y) + height
            val distance = offsetX * offsetX + offsetY * offsetY
            if (distance < best || (distance == best && stepY < bestY)) {
                best = distance
                bestY = stepY
                spot = EdgeSpot.pack(stepX, stepY)
            }
        }
        return spot
    }

    companion object {
        fun of(follow: EdgeFollow, side: EdgeSide, outline: BodyOutline, rect: PixelRect, width: Int, height: Int): EdgeRoute {
            val shape = outline.rows(rect) ?: return point(rect.x0, rect.y0)
            val boxWidth = maxOf(1, width)
            val boxHeight = maxOf(1, height)
            return when (follow) {
                EdgeFollow.OUTSIDE -> EdgeLoop(shape.grown(boxWidth, boxHeight)).route(side, 0, 0)
                EdgeFollow.LINE -> EdgeLoop(shape).route(side, -boxWidth / 2, -boxHeight / 2)
                EdgeFollow.INSIDE -> inside(shape, side, boxWidth, boxHeight)
            }
        }

        fun point(x: Int, y: Int): EdgeRoute = EdgeRoute(intArrayOf(x, x), intArrayOf(y, y), intArrayOf(PathMotion.NONE))

        private fun inside(shape: EdgeRows, side: EdgeSide, width: Int, height: Int): EdgeRoute {
            var fitWidth = minOf(width, shape.width)
            var fitHeight = minOf(height, shape.count)
            var rows = shape.eroded(fitWidth, fitHeight)
            while (rows == null) {
                fitWidth = maxOf(1, fitWidth - 1)
                fitHeight = maxOf(1, fitHeight - 1)
                rows = shape.eroded(fitWidth, fitHeight)
            }
            return EdgeLoop(rows).route(side, (fitWidth - width) / 2, (fitHeight - height) / 2)
        }
    }
}

private class EdgeLoop(rows: EdgeRows) {
    private val top = rows.top
    private val lefts = rows.lefts
    private val rights = rows.rights
    private val xs = IntList()
    private val ys = IntList()
    private val topEnd: Int
    private val bottomStart: Int
    private val bottomEnd: Int

    init {
        add(lefts[0], top)
        walkTo(rights[0], top)
        topEnd = xs.size - 1
        for (row in 1 until rows.count) {
            down(row)
        }
        bottomStart = xs.size - 1
        walkTo(lefts[rows.count - 1], top + rows.count - 1)
        bottomEnd = xs.size - 1
        for (row in rows.count - 2 downTo 0) {
            up(row)
        }
    }

    private val closing = xs.size - 1

    private fun add(x: Int, y: Int) {
        val last = xs.size - 1
        if (last >= 0 && xs[last] == x && ys[last] == y) return

        xs.add(x)
        ys.add(y)
    }

    private fun walkTo(x: Int, y: Int) {
        var at = xs[xs.size - 1]
        while (at != x) {
            at += (x - at).sign
            add(at, y)
        }
    }

    private fun down(row: Int) {
        val y = top + row
        val to = rights[row]
        if (to < xs[xs.size - 1]) walkTo(to + 1, y - 1)
        val x = xs[xs.size - 1]
        add(if (to > x) x + 1 else to, y)
        walkTo(to, y)
    }

    private fun up(row: Int) {
        val y = top + row
        val to = lefts[row]
        if (to > xs[xs.size - 1]) walkTo(to - 1, y + 1)
        val x = xs[xs.size - 1]
        add(if (to < x) x - 1 else to, y)
        walkTo(to, y)
    }

    private fun firstAt(x: Int, from: Int, to: Int): Int {
        for (i in from..to) {
            if (xs[i] == x) return i
        }
        return to
    }

    private fun lastAt(x: Int, from: Int, to: Int): Int {
        for (i in to downTo from) {
            if (xs[i] == x) return i
        }
        return from
    }

    fun route(side: EdgeSide, offsetX: Int, offsetY: Int): EdgeRoute {
        if (closing == 0) return EdgeRoute.point(xs[0] + offsetX, ys[0] + offsetY)

        val rightmost = rights.max()
        val leftmost = lefts.min()
        val start = lastAt(leftmost, bottomEnd, closing)
        val shift = closing - start
        val rightTop = firstAt(rightmost, topEnd, bottomStart) + shift
        val rightBottom = lastAt(rightmost, topEnd, bottomStart) + shift
        val leftBottom = firstAt(leftmost, bottomEnd, closing) + shift
        val marks = intArrayOf(0, shift, topEnd + shift, rightTop, rightBottom, bottomStart + shift, bottomEnd + shift, leftBottom)
        val steps = IntArray(closing) { facingAt(it, marks) }
        val walk = Walk(start, steps, offsetX, offsetY)
        return when (side) {
            EdgeSide.TOP -> walk.forward(rightTop)
            EdgeSide.BOTTOM -> walk.backward(leftBottom, rightBottom)
            EdgeSide.PERIMETER -> walk.forward(closing)
        }
    }

    private fun facingAt(step: Int, marks: IntArray): Int {
        var part = marks.size - 1
        while (step < marks[part]) {
            part--
        }
        val first = PARTS[part]
        if (part % 2 == 1) return first

        val to = if (part + 1 < marks.size) marks[part + 1] else closing
        return if ((step - marks[part]) * 2 < to - marks[part]) first else PARTS[part + 1]
    }

    private inner class Walk(
        private val start: Int,
        private val steps: IntArray,
        private val offsetX: Int,
        private val offsetY: Int,
    ) {
        private fun x(at: Int): Int = xs[(at + start) % closing] + offsetX

        private fun y(at: Int): Int = ys[(at + start) % closing] + offsetY

        fun forward(to: Int): EdgeRoute {
            val built = RouteBuilder(x(0), y(0))
            for (at in 0 until to) {
                built.step(x(at + 1), y(at + 1), steps[at])
            }
            return built.route()
        }

        fun backward(from: Int, to: Int): EdgeRoute {
            val built = RouteBuilder(x(from), y(from))
            for (at in from downTo to + 1) {
                built.step(x(at - 1), y(at - 1), steps[at - 1] xor 1)
            }
            return built.route()
        }
    }

    private companion object {
        val PARTS = intArrayOf(
            PathMotion.UP,
            PathMotion.RIGHT,
            PathMotion.RIGHT,
            PathMotion.DOWN,
            PathMotion.DOWN,
            PathMotion.LEFT,
            PathMotion.LEFT,
            PathMotion.UP,
        )
    }
}

private class RouteBuilder(private var lastX: Int, private var lastY: Int) {
    private val xs = IntList().also { it.add(lastX) }
    private val ys = IntList().also { it.add(lastY) }
    private val facings = IntList()

    fun step(x: Int, y: Int, facing: Int) {
        val last = facings.size - 1
        val straight = last >= 0 && facings[last] == facing &&
            (xs[last + 1] - xs[last]).sign == x - lastX && (ys[last + 1] - ys[last]).sign == y - lastY
        if (straight) {
            xs[last + 1] = x
            ys[last + 1] = y
        } else {
            xs.add(x)
            ys.add(y)
            facings.add(facing)
        }
        lastX = x
        lastY = y
    }

    fun route(): EdgeRoute {
        if (facings.size == 0) return EdgeRoute.point(lastX, lastY)

        return EdgeRoute(xs.toIntArray(), ys.toIntArray(), facings.toIntArray())
    }
}

internal object EdgeSpot {
    fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

    fun x(spot: Long): Int = (spot shr 32).toInt()

    fun y(spot: Long): Int = spot.toInt()
}

internal class EdgeRoutes(private val outline: BodyOutline) {
    private class Cached(
        val rect: PixelRect,
        val width: Int,
        val height: Int,
        val route: EdgeRoute?,
        val geometry: PathGeometry?,
        val rows: EdgeRows? = null,
    ) {
        val spots = arrayOfNulls<PlacedSpot>(PLACES)

        fun matches(rect: PixelRect, width: Int, height: Int): Boolean =
            this.rect == rect && this.width == width && this.height == height
    }

    private class PlacedSpot(val place: OrnamentAnchor, val spot: Long)

    private val ways = arrayOfNulls<Cached>(WAYS)

    fun geometry(path: SpritePath, rect: PixelRect, width: Int, height: Int): PathGeometry {
        val way = way(rect, width, height)
        ways[way]?.takeIf { it.matches(rect, width, height) }?.geometry?.let { return it }

        val route = EdgeRoute.of(checkNotNull(path.follow), path.side, outline, rect, width, height)
        val geometry = PathGeometry(path, route)
        ways[way] = Cached(rect, width, height, route, geometry)
        return geometry
    }

    fun route(follow: EdgeFollow, side: EdgeSide, rect: PixelRect, width: Int, height: Int): EdgeRoute {
        val way = way(rect, width, height)
        ways[way]?.takeIf { it.matches(rect, width, height) }?.route?.let { return it }

        val route = EdgeRoute.of(follow, side, outline, rect, width, height)
        ways[way] = Cached(rect, width, height, route, null)
        return route
    }

    fun rows(rect: PixelRect, width: Int, height: Int): EdgeRows? {
        val way = way(rect, width, height)
        ways[way]?.takeIf { it.matches(rect, width, height) }?.rows?.let { return it }

        val rows = outline.rows(rect)?.fitted(width, height)
        ways[way] = Cached(rect, width, height, null, null, rows)
        return rows
    }

    fun spot(follow: EdgeFollow, place: OrnamentAnchor, rect: PixelRect, width: Int, height: Int): Long {
        val way = way(rect, width, height)
        val cached = ways[way]?.takeIf { it.matches(rect, width, height) && it.route != null }
            ?: Cached(rect, width, height, EdgeRoute.of(follow, EdgeSide.PERIMETER, outline, rect, width, height), null)
                .also { ways[way] = it }
        val spots = cached.spots
        spots.firstOrNull { it?.place === place }?.let { return it.spot }

        val spot = checkNotNull(cached.route).nearest(place.edgeX(rect), place.edgeY(rect), width, height)
        spots[if (spots[0] == null) 0 else 1] = PlacedSpot(place, spot)
        return spot
    }

    private fun way(rect: PixelRect, width: Int, height: Int): Int =
        ((rect.width * MIX + rect.height) * MIX + width * MIX + height) and (WAYS - 1)

    private companion object {
        const val WAYS = 64
        const val MIX = 31
        const val PLACES = 2
    }
}
