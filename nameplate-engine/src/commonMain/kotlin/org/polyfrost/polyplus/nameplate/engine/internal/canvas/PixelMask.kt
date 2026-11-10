package org.polyfrost.polyplus.nameplate.engine.internal.canvas

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelCanvas
import kotlin.math.ceil
import kotlin.math.floor

internal class PixelMask {
    private val pixels = HashSet<Long>()

    val size: Int
        get() = pixels.size

    fun contains(x: Int, y: Int): Boolean = PixelGrid.key(x, y) in pixels

    fun add(x: Int, y: Int) {
        pixels.add(PixelGrid.key(x, y))
    }

    fun disc(cx: Double, cy: Double, radius: Double) {
        for (y in floor(cy - radius).toInt()..ceil(cy + radius).toInt()) {
            for (x in floor(cx - radius).toInt()..ceil(cx + radius).toInt()) {
                val dx = x + 0.5 - cx
                val dy = y + 0.5 - cy
                if (dx * dx + dy * dy <= radius * radius) {
                    add(x, y)
                }
            }
        }
    }

    fun outline(): PixelMask {
        val ring = PixelMask()
        for (key in pixels) {
            val x = PixelGrid.keyX(key)
            val y = PixelGrid.keyY(key)
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val neighbor = PixelGrid.key(x + dx, y + dy)
                    if (neighbor !in pixels) {
                        ring.pixels.add(neighbor)
                    }
                }
            }
        }

        return ring
    }

    fun paint(canvas: PixelCanvas, color: Int) {
        for (key in pixels) {
            canvas[PixelGrid.keyX(key), PixelGrid.keyY(key)] = color
        }
    }
}
