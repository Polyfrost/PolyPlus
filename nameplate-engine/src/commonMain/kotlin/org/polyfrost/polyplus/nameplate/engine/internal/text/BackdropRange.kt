package org.polyfrost.polyplus.nameplate.engine.internal.text

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette

internal class BackdropRange {
    private val palettes = ArrayList<ResolvedPalette>()
    private val refs = LinkedHashSet<Long>()
    private val colors = LinkedHashSet<Int>()

    fun palette(palette: ResolvedPalette) {
        if (palettes.none { it === palette }) {
            palettes.add(palette)
        }
    }

    fun image(image: PixelImage, at: Placement, x0: Int, y0: Int, x1: Int, y1: Int) {
        if (image.rectCount == 0 || at.opacity <= 0) return

        val localX0 = if (at.mirrorX) at.x + at.width - x1 else x0 - at.x
        val localX1 = if (at.mirrorX) at.x + at.width - x0 else x1 - at.x
        val localY0 = y0 - at.y
        val localY1 = y1 - at.y
        val opacity = at.opacity.coerceAtMost(OPAQUE)
        if (image.usesPalette) {
            image.sampleRefs(localX0, localY0, localX1, localY1) { ref ->
                refs.add((opacity.toLong() shl Int.SIZE_BITS) or (ref.toLong() and REF_MASK))
            }
        } else {
            image.sample(localX0, localY0, localX1, localY1) { argb -> colors.add(scaleAlpha(argb, opacity)) }
        }
    }

    fun color(argb: Int) {
        colors.add(argb)
    }

    fun emit(sink: (Int) -> Unit) {
        for (color in colors) {
            sink(color)
        }

        for (entry in refs) {
            val opacity = (entry ushr Int.SIZE_BITS).toInt()
            val ref = entry.toInt()
            for (palette in palettes) {
                sink(scaleAlpha(palette.color(ref), opacity))
            }
        }
    }

    class Placement(val x: Int, val y: Int, val mirrorX: Boolean, val width: Int, val opacity: Int)

    companion object {
        private const val OPAQUE = 255
        private const val REF_MASK = 0xFFFFFFFFL

        fun scaleAlpha(argb: Int, opacity: Int): Int {
            if (opacity >= OPAQUE) return argb
            val alpha = ((argb ushr 24) * opacity + OPAQUE / 2) / OPAQUE
            return (alpha shl 24) or (argb and 0xFFFFFF)
        }
    }
}
