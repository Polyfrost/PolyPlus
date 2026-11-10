package org.polyfrost.polyplus.nameplate.engine.api.canvas

class PixelGrid {
    internal val colors = IntArray(WINDOW.width * WINDOW.height)
    internal val generations = IntArray(colors.size)
    private var latestGeneration = 0

    internal fun nextGeneration(): Int {
        if (latestGeneration == Int.MAX_VALUE) {
            generations.fill(0)
            latestGeneration = 0
        }

        return ++latestGeneration
    }

    internal fun index(x: Int, y: Int): Int {
        val cx = x - WINDOW.x0
        val cy = y - WINDOW.y0
        if (cx < 0 || cy < 0 || cx >= WINDOW.width || cy >= WINDOW.height) return -1

        return cy * WINDOW.width + cx
    }

    companion object {
        internal val WINDOW: PixelRect = PixelRect(-192, -64, 448, 64)

        private const val KEY_BIAS = 1 shl 20

        internal fun key(x: Int, y: Int): Long = ((y + KEY_BIAS).toLong() shl 32) or (x + KEY_BIAS).toLong()

        internal fun keyX(key: Long): Int = key.toInt() - KEY_BIAS

        internal fun keyY(key: Long): Int = (key ushr 32).toInt() - KEY_BIAS
    }
}
