package org.polyfrost.polyplus.client.ping

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.polyfrost.compose.minecraft.mc.McPlayerFaces
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf

internal object PingFaces {
    const val SIDE = 8

    private const val CACHE_LIMIT = 64

    private class Sampled(val source: PolyBitmap, val pixels: IntArray)

    private val sampled = HashMap<String, Sampled>()

    fun pixels(player: String): IntArray? {
        val face = McPlayerFaces.face(seedOf(player)) ?: return null
        sampled[player]?.takeIf { it.source === face }?.let { return it.pixels }
        val width = face.width
        val height = face.height
        if (width <= 0 || height <= 0) return null
        val px = face.pixels
        val result = IntArray(SIDE * SIDE) { i ->
            val x = ((i % SIDE) * 2 + 1) * width / (SIDE * 2)
            val y = ((i / SIDE) * 2 + 1) * height / (SIDE * 2)
            val o = (y * width + x) * 4
            ((px[o + 3].toInt() and 0xFF) shl 24) or ((px[o].toInt() and 0xFF) shl 16) or
                ((px[o + 1].toInt() and 0xFF) shl 8) or (px[o + 2].toInt() and 0xFF)
        }
        if (sampled.size >= CACHE_LIMIT) sampled.clear()
        sampled[player] = Sampled(face, result)
        return result
    }

    fun placeholder(player: String): Int = Color.hsv(seedOf(player).hashCode().mod(360).toFloat(), 0.45f, 0.55f).toArgb()
}
