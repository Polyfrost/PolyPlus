package org.polyfrost.polyplus.nameplate.engine.api.text

import kotlin.math.abs

class LetterFrame {
    val dx: IntArray = IntArray(MAX_GLYPHS)
    val dy: IntArray = IntArray(MAX_GLYPHS)
    val opacity: IntArray = IntArray(MAX_GLYPHS)
    val color: IntArray = IntArray(MAX_GLYPHS)
    val shadow: IntArray = IntArray(MAX_GLYPHS)
    val outline: IntArray = IntArray(MAX_GLYPHS)
    val scale: IntArray = IntArray(MAX_GLYPHS)
    val rotation: IntArray = IntArray(MAX_GLYPHS)

    var count: Int = 0
        private set

    var moving: Boolean = false
        private set

    internal val inPlace: Boolean
        get() = (0 until count).all { glyph ->
            abs(dx[glyph]) <= READABLE_SHIFT &&
                abs(dy[glyph]) <= READABLE_SHIFT &&
                opacity[glyph] >= HALF &&
                abs(scale[glyph] - SCALE_UNITS) <= 1 &&
                (rotation[glyph] <= READABLE_TURN || rotation[glyph] >= FULL_TURN - READABLE_TURN)
        }

    fun isStill(glyph: Int): Boolean =
        dx[glyph] == 0 &&
            dy[glyph] == 0 &&
            opacity[glyph] == OPAQUE &&
            scale[glyph] == SCALE_UNITS &&
            rotation[glyph] == 0 &&
            color[glyph] == KEEP &&
            shadow[glyph] == KEEP &&
            outline[glyph] == KEEP

    fun sameRun(first: Int, second: Int): Boolean =
        dx[first] == dx[second] && dy[first] == dy[second] && opacity[first] == opacity[second]

    fun scaleOf(glyph: Int): Float = scale[glyph].toFloat() / SCALE_UNITS

    internal fun reset(count: Int) {
        this.count = count.coerceIn(0, MAX_GLYPHS)
        moving = false
        dx.fill(0)
        dy.fill(0)
        opacity.fill(OPAQUE)
        color.fill(KEEP)
        shadow.fill(KEEP)
        outline.fill(KEEP)
        scale.fill(SCALE_UNITS)
        rotation.fill(0)
    }

    internal fun finish() {
        moving = false
        for (i in 0 until count) {
            if (!isStill(i)) {
                moving = true
                return
            }
        }
    }

    internal fun copyFrom(source: LetterFrame) {
        count = source.count
        moving = source.moving
        source.dx.copyInto(dx)
        source.dy.copyInto(dy)
        source.opacity.copyInto(opacity)
        source.color.copyInto(color)
        source.shadow.copyInto(shadow)
        source.outline.copyInto(outline)
        source.scale.copyInto(scale)
        source.rotation.copyInto(rotation)
    }

    companion object {
        const val MAX_GLYPHS = 24
        const val OPAQUE = 255
        const val KEEP = -1
        const val OFF = -2
        internal const val SCALE_UNITS = 4
        internal const val MIN_SCALE = 1
        internal const val MAX_SCALE = 12

        internal const val READABLE_SHIFT = 2
        internal const val READABLE_TURN = 15

        private const val HALF = 128
        private const val FULL_TURN = 360
    }
}
