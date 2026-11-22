package org.polyfrost.polyplus.nameplate.engine.api.text

object BackdropPixels {
    internal const val BACKDROP = 0x808080

    fun collect(
        backdrop: TextBackdrop,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        hint: IntArray,
    ): IntArray {
        val colors = LinkedHashSet<Int>()
        backdrop.sample(width, height, x0, y0, x1, y1) { argb ->
            val alpha = argb ushr 24
            if (alpha == 0xFF) {
                colors.add(argb and 0xFFFFFF)
            } else if (alpha != 0) {
                colors.add(Contrast.compositeOver(argb, BACKDROP))
            }
        }

        for (rgb in hint) {
            colors.add(rgb and 0xFFFFFF)
        }

        return colors.toIntArray()
    }
}
