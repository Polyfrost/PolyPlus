package org.polyfrost.polyplus.nameplate.engine.api.canvas

import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.internal.strip.PixelStrips

class PixelImage internal constructor(
    private val rects: IntArray,
    private val own: ResolvedPalette?,
    private val strips: PixelStrips = PixelStrips.NONE,
) {
    private val refLimit: Int = run {
        var limit = 0
        for (i in REF until rects.size step STRIDE) {
            if (rects[i] and ResolvedPalette.BLEND_FLAG == 0) {
                limit = maxOf(limit, rects[i] + 1)
            }
        }
        limit
    }

    internal fun geometry(): IntArray = rects

    val rectCount: Int
        get() = rects.size / STRIDE

    val usesPalette: Boolean
        get() = own == null

    internal val quadCount: Int
        get() = rectCount - strips.covered + strips.count

    val stripCount: Int
        get() = strips.count

    internal val stripBytes: Int
        get() = strips.bytes

    fun stripOf(rect: Int): Int = if (strips.owners.isEmpty()) -1 else strips.owners[rect]

    fun copyStrips(): IntArray {
        val geometry = strips.geometry
        val out = IntArray(strips.count * STRIP_STRIDE)
        for (s in 0 until strips.count) {
            geometry.copyInto(out, s * STRIP_STRIDE, s * PixelStrips.STRIDE, s * PixelStrips.STRIDE + STRIP_STRIDE)
        }
        return out
    }

    fun stripColors(strip: Int, out: IntArray): Int = stripColors(strip, ownPalette("resolve"), out)

    fun stripColors(strip: Int, palette: ResolvedPalette, out: IntArray): Int {
        checkSize(palette)
        val at = strip * PixelStrips.STRIDE
        val period = strips.geometry[at + PixelStrips.PERIOD]
        val pattern = strips.geometry[at + PixelStrips.PATTERN]
        for (k in 0 until period) {
            out[k] = palette.color(strips.patterns[pattern + k])
        }
        return period
    }

    fun resolve(): IntArray = resolve(ownPalette("resolve"))

    fun resolve(palette: ResolvedPalette): IntArray {
        checkSize(palette)
        val resolved = rects.copyOf()
        for (i in REF until resolved.size step STRIDE) {
            resolved[i] = palette.color(resolved[i])
        }

        return resolved
    }

    fun sample(x0: Int, y0: Int, x1: Int, y1: Int, sink: (Int) -> Unit) {
        sample(ownPalette("sample"), x0, y0, x1, y1, sink)
    }

    fun sample(palette: ResolvedPalette, x0: Int, y0: Int, x1: Int, y1: Int, sink: (Int) -> Unit) {
        if (rects.isEmpty()) return

        checkSize(palette)
        for (i in rects.indices step STRIDE) {
            if (rects[i] < x1 && rects[i + 2] > x0 && rects[i + 1] < y1 && rects[i + 3] > y0) {
                sink(palette.color(rects[i + REF]))
            }
        }
    }

    internal fun sampleRefs(x0: Int, y0: Int, x1: Int, y1: Int, sink: (Int) -> Unit) {
        check(usesPalette) { "Painted in colors, not palette slots; sample it without refs" }
        for (i in rects.indices step STRIDE) {
            if (rects[i] < x1 && rects[i + 2] > x0 && rects[i + 1] < y1 && rects[i + 3] > y0) {
                sink(rects[i + REF])
            }
        }
    }

    private fun ownPalette(action: String): ResolvedPalette =
        checkNotNull(own) { "Painted in palette slots; $action it with a palette" }

    private fun checkSize(palette: ResolvedPalette) {
        require(palette.size >= refLimit) { "Palette has ${palette.size} entries; image needs $refLimit" }
    }

    companion object {
        val EMPTY: PixelImage = PixelImage(IntArray(0), ResolvedPalette.EMPTY)

        internal const val STRIDE = PixelStrips.RECT_STRIDE
        internal const val REF = PixelStrips.RECT_REF

        const val STRIP_STRIDE = 5
        const val MAX_STRIP_PERIOD = PixelStrips.MAX_PERIOD
        const val MAX_STRIP_LENGTH = PixelStrips.MAX_LENGTH
    }
}
