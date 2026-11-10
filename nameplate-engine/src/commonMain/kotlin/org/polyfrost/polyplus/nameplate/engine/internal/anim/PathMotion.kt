package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PathEnd
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PathStop
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpritePath
import org.polyfrost.polyplus.nameplate.engine.internal.strip.IntList
import kotlin.math.abs
import kotlin.math.roundToInt

internal class WalkerSpot(val x: Int, val y: Int, val frame: Int, val flip: Boolean)

private class WalkedRoute(val xs: IntArray, val ys: IntArray, val pauses: IntArray, val facings: IntArray?) {
    private val segments = xs.size - 1

    private fun lengthOf(segment: Int): Int = maxOf(abs(xs[segment + 1] - xs[segment]), abs(ys[segment + 1] - ys[segment]))

    fun reversed(): WalkedRoute = WalkedRoute(
        xs.reversedArray(),
        ys.reversedArray(),
        pauses.reversedArray(),
        facings?.let { steps -> IntArray(steps.size) { PathMotion.opposite(steps[steps.size - 1 - it]) } },
    )

    fun stoppedAt(stops: List<PathStop>): WalkedRoute {
        if (stops.isEmpty()) return this

        val total = (0 until segments).sumOf(::lengthOf)
        val builtXs = IntList()
        val builtYs = IntList()
        val builtPauses = IntList()
        val builtFacings = IntList()
        var next = 0
        var along = 0
        for (point in xs.indices) {
            builtXs.add(xs[point])
            builtYs.add(ys[point])
            builtPauses.add(pauses[point])
            val length = if (point < segments) lengthOf(point) else 0
            var newest = along
            while (next < stops.size) {
                val target = (stops[next].at * total).roundToInt()
                if (point < segments && target >= along + length) break
                if (target > newest) {
                    builtXs.add(xs[point] + (xs[point + 1] - xs[point]) * (target - along) / length)
                    builtYs.add(ys[point] + (ys[point + 1] - ys[point]) * (target - along) / length)
                    builtPauses.add(0)
                    facings?.let { builtFacings.add(it[point]) }
                    newest = target
                }
                builtPauses[builtPauses.size - 1] += stops[next].pause
                next++
            }
            if (point < segments) {
                facings?.let { builtFacings.add(it[point]) }
            }
            along += length
        }
        return WalkedRoute(builtXs.toIntArray(), builtYs.toIntArray(), builtPauses.toIntArray(), facings?.let { builtFacings.toIntArray() })
    }

    companion object {
        fun of(path: SpritePath, xs: IntArray, ys: IntArray, pauses: IntArray, facings: IntArray?): WalkedRoute {
            val forward = WalkedRoute(xs, ys, pauses, facings)
            return (if (path.reverse) forward.reversed() else forward).stoppedAt(path.stops)
        }
    }
}

internal class PathGeometry private constructor(private val path: SpritePath, walked: WalkedRoute) {
    constructor(path: SpritePath, rect: PixelRect) : this(
        path,
        WalkedRoute.of(
            path,
            IntArray(path.anchors.size) { path.anchors[it].x(rect) + path.offsetsX[it] },
            IntArray(path.anchors.size) { path.anchors[it].y(rect) + path.offsetsY[it] },
            path.pauses,
            null,
        ),
    )

    constructor(path: SpritePath, route: EdgeRoute) : this(
        path,
        WalkedRoute.of(path, route.xs, route.ys, IntArray(route.xs.size), route.facings),
    )

    val xs: IntArray = walked.xs
    val ys: IntArray = walked.ys
    private val pauses = walked.pauses
    private val stepFacings = walked.facings
    private val count = xs.size
    private val segments = count - 1
    private val lengths = IntArray(segments) { maxOf(abs(xs[it + 1] - xs[it]), abs(ys[it + 1] - ys[it])) }
    private val starts = LongArray(count).also { sums ->
        for (i in 0 until segments) {
            sums[i + 1] = sums[i] + lengths[i].toLong() * PathMotion.UNIT
        }
    }
    private val total: Long = starts[segments]
    private val sequence = if (path.end == PathEnd.BOUNCE) 2 * segments else segments
    private val lapSpan = if (path.end == PathEnd.BOUNCE) 2 * total else total
    private val horizontals: IntArray? = stepFacings?.let { horizontalTable() }
    private val paused = pauses.any { it > 0 }
    private val moving: Long = if (paused && path.laps > 0) lapPeriod() - waiting() else 0L
    private val travels: LongArray = if (paused) travelTable() else NO_STEPS
    private val departures: LongArray = if (paused) departureTable() else NO_STEPS
    private val period: Long = if (paused) fullPeriod() else 0L

    fun walker(time: Int, index: Int, shift: Long = 0L, speed: Int = UNSET): WalkerSpot {
        val motion = when {
            speed == UNSET -> FORWARD
            speed == 0 -> STILL
            speed < 0 -> REVERSED
            else -> FORWARD
        }
        return if (paused) {
            val shifted = time.toLong() + shift.floorDiv(path.constantSpeed.toLong())
            phased((shifted - walkerDelay(index)).toInt(), time, motion)
        } else {
            distanced(time, index, shift, motion)
        }
    }

    private fun walkerDelay(index: Int): Long = if (path.even) period * index / path.walkers else index.toLong() * pauseSpacing()

    private fun pauseSpacing(): Long {
        val distance = path.spacing.toLong() * PathMotion.UNIT
        return if (path.laps > 0) rounded(distance * moving, lapSpan) else rounded(distance, path.constantSpeed.toLong())
    }

    private fun rounded(value: Long, over: Long): Long = if (over == 0L) 0L else (2L * value + over) / (2L * over)

    private fun distanced(time: Int, index: Int, shift: Long, motion: Int): WalkerSpot {
        val traveled = traveledBy(time) + shift - gapBefore(index)
        if (total == 0L) return spot(0, 0L, time, STILL)

        return when (path.end) {
            PathEnd.WRAP -> forward(traveled.mod(total), time, motion)
            PathEnd.BOUNCE -> {
                val folded = traveled.mod(2 * total)
                if (folded < total) forward(folded, time, motion) else backward(folded - total, time, motion)
            }
            PathEnd.STOP -> when {
                traveled <= 0L -> spot(0, 0L, time, STILL)
                traveled >= total -> spot(segments - 1, lengthOf(segments - 1), time, STILL)
                else -> forward(traveled, time, motion)
            }
        }
    }

    private fun traveledBy(time: Int): Long =
        if (path.laps > 0) (time.toLong() * path.laps * lapSpan).floorDiv(path.cycle) else path.distance(time)

    private fun gapBefore(index: Int): Long =
        if (path.even) lapSpan * index / path.walkers else index.toLong() * path.spacing * PathMotion.UNIT

    private fun forward(distance: Long, time: Int, motion: Int): WalkerSpot {
        val segment = lastAtMost(starts, segments, distance)
        return spot(segment, distance - starts[segment], time, motion)
    }

    private fun lastAtMost(table: LongArray, size: Int, value: Long): Int {
        var low = 0
        var high = size - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (table[middle] <= value) low = middle else high = middle - 1
        }
        return low
    }

    private fun backward(back: Long, time: Int, motion: Int): WalkerSpot {
        val distance = total - back
        val segment = lastAtMost(starts, segments, distance - 1)
        return spot(2 * segments - 1 - segment, starts[segment + 1] - distance, time, motion)
    }

    private fun phased(walkerTime: Int, time: Int, motion: Int): WalkerSpot {
        val left = when {
            path.end != PathEnd.STOP -> if (period == 0L) 0L else walkerTime.toLong().mod(period)
            walkerTime < 0 -> 0L
            else -> walkerTime.toLong()
        }
        if (left < departures[0]) return spot(0, 0L, time, STILL)
        val entry = lastAtMost(departures, sequence, left)
        val local = left - departures[entry]
        val travel = travels[entry]
        if (local >= travel) return spot(entry, lengthOf(entry), time, STILL)
        return spot(entry, localDistance(entry, local, travel), time, motion)
    }

    private fun localDistance(entry: Int, local: Long, travel: Long): Long =
        if (path.laps > 0) local * lengthOf(entry) / travel else minOf(local * path.constantSpeed, lengthOf(entry))

    private fun lapPeriod(): Long = path.cycle.toLong() / path.laps

    private fun waiting(): Long {
        var waits = pauses[0].toLong()
        for (entry in 0 until sequence) {
            waits += pauseAfter(entry)
        }
        return waits
    }

    private fun travelTable(): LongArray {
        if (path.laps == 0) return LongArray(sequence) { (lengthOf(it) + path.constantSpeed - 1) / path.constantSpeed }

        var walked = 0L
        var arrived = 0L
        return LongArray(sequence) { entry ->
            walked += lengthOf(entry)
            val reached = rounded(walked * moving, lapSpan)
            (reached - arrived).also { arrived = reached }
        }
    }

    private fun departureTable(): LongArray {
        val table = LongArray(sequence)
        table[0] = pauses[0].toLong()
        for (entry in 1 until sequence) {
            table[entry] = table[entry - 1] + travels[entry - 1] + pauseAfter(entry - 1)
        }
        return table
    }

    private fun fullPeriod(): Long =
        if (path.laps > 0) lapPeriod() else departures[sequence - 1] + travels[sequence - 1] + pauseAfter(sequence - 1)

    private fun pauseAfter(entry: Int): Int =
        if (path.end == PathEnd.BOUNCE && entry == sequence - 1) 0 else pauses[endPoint(entry)]

    private fun lengthOf(entry: Int): Long = lengths[segmentOf(entry)].toLong() * PathMotion.UNIT

    private fun segmentOf(entry: Int): Int = if (entry < segments) entry else 2 * segments - 1 - entry

    private fun startPoint(entry: Int): Int = if (entry < segments) entry else segmentOf(entry) + 1

    private fun endPoint(entry: Int): Int = if (entry < segments) entry + 1 else segmentOf(entry)

    private fun spot(entry: Int, local: Long, time: Int, motion: Int): WalkerSpot {
        val from = startPoint(entry)
        val to = endPoint(entry)
        val length = lengthOf(entry)
        val x = if (length == 0L) xs[from] else xs[from] + ((xs[to] - xs[from]) * local / length).toInt()
        val y = if (length == 0L) ys[from] else ys[from] + ((ys[to] - ys[from]) * local / length).toInt()
        if (!path.hasFacings) return WalkerSpot(x, y, PathMotion.NORMAL_FRAME, false)

        val reversed = motion == REVERSED
        var facing = turned(facingOf(entry), reversed)
        var frames = if (facing == PathMotion.UP || facing == PathMotion.DOWN) path.facings[facing] else null
        var flip = false
        if (frames == null) {
            if (facing != PathMotion.RIGHT && facing != PathMotion.LEFT) {
                facing = turned(lastHorizontal(entry), reversed)
            }
            frames = path.facings[facing]
            if (frames == null) {
                frames = path.facings[facing xor 1]
                flip = true
            }
        }
        val shown = checkNotNull(frames)
        return WalkerSpot(x, y, if (motion != STILL) shown[time.mod(shown.size)] else shown[0], flip)
    }

    private fun turned(facing: Int, reversed: Boolean): Int = if (reversed) PathMotion.opposite(facing) else facing

    private companion object {
        val NO_STEPS = LongArray(0)
        const val FORWARD = 0
        const val REVERSED = 1
        const val STILL = 2
    }

    private fun facingOf(entry: Int): Int {
        val table = stepFacings
        if (table != null) return turned(table[segmentOf(entry)], entry >= segments)

        val dx = xs[endPoint(entry)] - xs[startPoint(entry)]
        val dy = ys[endPoint(entry)] - ys[startPoint(entry)]
        return when {
            dx == 0 && dy == 0 -> PathMotion.NONE
            abs(dx) >= abs(dy) -> if (dx > 0) PathMotion.RIGHT else PathMotion.LEFT
            else -> if (dy > 0) PathMotion.DOWN else PathMotion.UP
        }
    }

    private fun horizontalTable(): IntArray {
        var last = PathMotion.RIGHT
        if (path.end != PathEnd.STOP) {
            for (entry in 0 until sequence) {
                last = horizontalOr(last, entry)
            }
        }
        return IntArray(sequence) { entry ->
            last = horizontalOr(last, entry)
            last
        }
    }

    private fun horizontalOr(fallback: Int, entry: Int): Int {
        val facing = facingOf(entry)
        return if (facing == PathMotion.RIGHT || facing == PathMotion.LEFT) facing else fallback
    }

    private fun lastHorizontal(entry: Int): Int {
        horizontals?.let { return it[entry] }

        val cyclic = path.end != PathEnd.STOP
        for (back in 0 until sequence) {
            val at = entry - back
            if (at < 0 && !cyclic) break
            val facing = facingOf(at.mod(sequence))
            if (facing == PathMotion.RIGHT || facing == PathMotion.LEFT) return facing
        }
        return PathMotion.RIGHT
    }
}

internal object PathMotion {
    const val UNIT = 256
    const val NORMAL_FRAME = -1
    const val RIGHT = 0
    const val LEFT = 1
    const val UP = 2
    const val DOWN = 3
    const val NONE = 4

    val FACINGS: List<String> = listOf("right", "left", "up", "down")

    fun opposite(facing: Int): Int = if (facing == NONE) NONE else facing xor 1
}
