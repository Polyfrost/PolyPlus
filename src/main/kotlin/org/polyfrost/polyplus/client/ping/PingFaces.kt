@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.ping

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHeads
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf

internal object PingFaces {
    const val SIDE = 8

    private const val CACHE_LIMIT = 64

    private class Sampled(val source: ImageBitmap, val pixels: IntArray)

    private val sampled = HashMap<String, Sampled>()

    fun bitmap(player: String): ImageBitmap? = PlayerHeads.face(seedOf(player))

    fun pixels(player: String): IntArray? {
        val face = bitmap(player) ?: return null
        sampled[player]?.takeIf { it.source === face }?.let { return it.pixels }
        val width = face.width
        val height = face.height
        if (width <= 0 || height <= 0) return null
        val full = IntArray(width * height)
        runCatching { face.readPixels(full, 0, 0, width, height, 0, width) }.getOrElse { return null }
        val result = IntArray(SIDE * SIDE) { i ->
            val x = ((i % SIDE) * 2 + 1) * width / (SIDE * 2)
            val y = ((i / SIDE) * 2 + 1) * height / (SIDE * 2)
            full[y * width + x]
        }
        if (sampled.size >= CACHE_LIMIT) sampled.clear()
        sampled[player] = Sampled(face, result)
        return result
    }

    fun placeholder(player: String): Int = Color.hsv(seedOf(player).hashCode().mod(360).toFloat(), 0.45f, 0.55f).toArgb()
}
