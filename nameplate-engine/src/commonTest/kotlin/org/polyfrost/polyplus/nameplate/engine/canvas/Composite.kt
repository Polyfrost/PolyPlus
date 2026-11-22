package org.polyfrost.polyplus.nameplate.engine.canvas

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.Depth
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.support.pixelKey
import org.polyfrost.polyplus.nameplate.engine.support.pixelKeyX
import org.polyfrost.polyplus.nameplate.engine.support.pixelKeyY

internal object Composite {
    fun pixels(painted: List<LayerPainter.Painted>, palette: ResolvedPalette): Map<Long, Int> {
        val pixels = HashMap<Long, Int>()
        for (op in painted) {
            for ((key, color) in place(op, palette)) {
                if (color ushr 24 != 0) {
                    pixels[key] = over(color, pixels[key])
                }
            }
        }

        return pixels
    }

    fun pixels(painted: List<LayerPainter.Painted>, palette: ResolvedPalette, depth: Depth): Map<Long, Int> =
        pixels(painted.filter { it.depth == depth }, palette)

    fun place(op: LayerPainter.Painted, palette: ResolvedPalette): Map<Long, Int> {
        val placed = HashMap<Long, Int>()
        for ((key, color) in Rects.pixels(op.image.resolve(palette))) {
            val x = if (op.mirrorX) op.x + op.width - 1 - pixelKeyX(key) else op.x + pixelKeyX(key)
            placed[pixelKey(x, op.y + pixelKeyY(key))] = color
        }

        return placed
    }

    fun over(source: Int, destination: Int?): Int {
        val sa = source ushr 24
        if (destination == null || sa == 255) {
            return source
        }
        if (sa == 0) {
            return destination
        }

        val da = destination ushr 24
        val oa = sa + div255(da * (255 - sa))
        var out = oa shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            val sc = (source ushr shift) and 0xFF
            val dc = (destination ushr shift) and 0xFF
            val blended = (sc * sa * 255 + dc * da * (255 - sa) + oa * 255 / 2) / (oa * 255)
            out = out or (blended shl shift)
        }

        return out
    }

    private fun div255(value: Int): Int = (value + 127) / 255
}
