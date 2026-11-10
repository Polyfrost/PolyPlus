package org.polyfrost.polyplus.nameplate.engine.api.palette

import org.polyfrost.polyplus.nameplate.engine.internal.color.ColorLerp

class ResolvedPalette internal constructor(
    private val colors: IntArray,
    private val stops: Array<IntArray> = emptyArray(),
) {
    val size: Int
        get() = colors.size

    fun color(ref: Int): Int {
        if (ref and BLEND_FLAG == 0) return colors[ref]

        val slot = (ref ushr SLOT_SHIFT) and FIELD_MASK
        val row = (ref ushr ROW_SHIFT) and FIELD_MASK
        val rows = (ref and FIELD_MASK) + 1
        return ColorLerp.along(stops[slot], row, rows)
    }

    internal fun copyColors(): IntArray = colors.copyOf()

    internal fun copyStops(): Array<IntArray> = stops.copyOf()

    companion object {
        val EMPTY: ResolvedPalette = ResolvedPalette(IntArray(0))

        internal const val BLEND_FLAG = 1 shl 30
        internal const val MAX_BLEND_ROWS = 1024

        private const val SLOT_SHIFT = 20
        private const val ROW_SHIFT = 10
        private const val FIELD_MASK = 0x3FF

        internal fun blendRef(slot: Int, row: Int, rows: Int): Int {
            require(slot in 0..FIELD_MASK && rows in 1..MAX_BLEND_ROWS && row in 0 until rows) {
                "Blend ref out of range: slot $slot, row $row of $rows"
            }
            return BLEND_FLAG or (slot shl SLOT_SHIFT) or (row shl ROW_SHIFT) or (rows - 1)
        }
    }
}
