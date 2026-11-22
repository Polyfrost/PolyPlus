package org.polyfrost.polyplus.nameplate.engine.api.shader

import kotlin.math.floor

class FrameClock {
    private var start = 0L
    private var running = false

    fun frame(ticks: Long, partial: Double, fps: Int, loop: Int): Int {
        if (!running || ticks < start) {
            start = ticks
            running = true
        }
        var frame = since(ticks, partial, fps, loop)
        if (frame >= LIMIT) {
            start = ticks
            frame = since(ticks, partial, fps, loop)
        }
        return frame.toInt()
    }

    private fun since(ticks: Long, partial: Double, fps: Int, loop: Int): Long {
        val window = window(loop)
        return absolute(ticks, partial, fps) - absolute(start, 0.0, fps).floorDiv(window) * window
    }

    companion object {
        const val TICKS_PER_SECOND = 20
        internal const val LIMIT = 15L shl 20
        private const val WINDOW = 1L shl 20

        internal fun absolute(ticks: Long, partial: Double, fps: Int): Long {
            val scaled = ticks * fps
            val rest = scaled.mod(TICKS_PER_SECOND) + partial * fps
            return scaled.floorDiv(TICKS_PER_SECOND) + floor(rest / TICKS_PER_SECOND).toLong()
        }

        fun window(loop: Int): Long {
            val period = maxOf(1, loop).toLong()
            return (WINDOW + period - 1) / period * period
        }
    }
}
