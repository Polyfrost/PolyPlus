package org.polyfrost.polyplus.nameplate.engine.internal.canvas

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect

internal class PixelField(window: PixelRect, private val sampler: Sampler) {
    private val x0 = window.x0
    private val y0 = window.y0
    private val width = window.width
    private val height = window.height
    private val values = IntArray(width * height)
    private val known = BooleanArray(width * height)
    private var frame = Int.MIN_VALUE

    fun interface Sampler {
        fun sample(x: Int, y: Int, frame: Int): Int
    }

    operator fun get(x: Int, y: Int, frame: Int): Int {
        val cx = x - x0
        val cy = y - y0
        if (cx < 0 || cy < 0 || cx >= width || cy >= height) return sampler.sample(x, y, frame)

        if (frame != this.frame) {
            known.fill(false)
            this.frame = frame
        }

        val index = cy * width + cx
        if (!known[index]) {
            values[index] = sampler.sample(x, y, frame)
            known[index] = true
        }

        return values[index]
    }
}
