package org.polyfrost.polyplus.nameplate.engine.api.canvas

data class PixelRect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val width: Int
        get() = x1 - x0

    val height: Int
        get() = y1 - y0

    fun expand(amount: Int): PixelRect = PixelRect(x0 - amount, y0 - amount, x1 + amount, y1 + amount)

    internal fun withMinWidth(minWidth: Int): PixelRect {
        val missing = minWidth - width
        if (missing <= 0) return this

        return PixelRect(x0 - missing / 2, y0, x1 + missing - missing / 2, y1)
    }

    companion object {
        const val GLYPH_HEIGHT = 8

        fun vanillaBackground(width: Int, height: Int): PixelRect = PixelRect(-1, -1, width, height + 1)
    }
}
