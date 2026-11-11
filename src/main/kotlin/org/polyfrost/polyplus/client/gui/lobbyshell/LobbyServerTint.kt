@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.CardSurface

internal object LobbyServerTint {
    private const val CACHE_LIMIT = 256
    private const val CHANNEL_SHIFT = 5
    private const val BUCKET_BITS = 3
    private const val SATURATION_FLOOR = 0.15f
    private const val MIN_BRIGHTNESS = 0.45f

    private val cache = object : LinkedHashMap<Int, Color>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Color>?): Boolean = size > CACHE_LIMIT
    }

    fun of(bytes: ByteArray): Color? {
        val key = bytes.contentHashCode()
        synchronized(cache) { cache[key] }?.let { return it.takeIf { it != Color.Unspecified } }
        val color = runCatching { dominant(bytes) }.getOrNull()
        synchronized(cache) { cache[key] = color ?: Color.Unspecified }
        return color
    }

    private fun dominant(bytes: ByteArray): Color? {
        val bitmap = PolyBitmap.decode(bytes) ?: return null
        val pixels = bitmap.pixels
        val buckets = 1 shl (BUCKET_BITS * 3)
        val weights = FloatArray(buckets)
        val reds = FloatArray(buckets)
        val greens = FloatArray(buckets)
        val blues = FloatArray(buckets)
        var i = 0
        while (i + 3 < pixels.size) {
            val r = pixels[i].toInt() and 0xFF
            val g = pixels[i + 1].toInt() and 0xFF
            val b = pixels[i + 2].toInt() and 0xFF
            val a = pixels[i + 3].toInt() and 0xFF
            i += 4
            if (a == 0) continue
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val saturation = if (max == 0) 0f else (max - min) / max.toFloat()
            val weight = a / 255f * (SATURATION_FLOOR + saturation)
            val bucket = (r shr CHANNEL_SHIFT shl (BUCKET_BITS * 2)) or (g shr CHANNEL_SHIFT shl BUCKET_BITS) or (b shr CHANNEL_SHIFT)
            weights[bucket] += weight
            reds[bucket] += r * weight
            greens[bucket] += g * weight
            blues[bucket] += b * weight
        }
        val best = weights.indices.maxByOrNull { weights[it] } ?: return null
        val total = weights[best]
        if (total <= 0f) return null
        var r = reds[best] / total / 255f
        var g = greens[best] / total / 255f
        var b = blues[best] / total / 255f
        val brightness = maxOf(r, g, b)
        if (brightness in 0.001f..MIN_BRIGHTNESS) {
            val lift = MIN_BRIGHTNESS / brightness
            r = (r * lift).coerceAtMost(1f)
            g = (g * lift).coerceAtMost(1f)
            b = (b * lift).coerceAtMost(1f)
        }
        return Color(r, g, b)
    }
}

@Composable
internal fun rememberServerTint(data: ServerData): Color {
    val bytes = data.iconBytes
    val tint = remember(bytes) { bytes?.takeIf { it.isNotEmpty() }?.let(LobbyServerTint::of) }
    return tint ?: OneConfigTheme.accent
}

@Composable
internal fun ServerBackdrop(data: ServerData, modifier: Modifier) {
    val tint = rememberServerTint(data)
    val ink = CardSurface
    Box(
        modifier
            .background(ink)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.6f), tint.copy(alpha = 0.22f), Color.Transparent)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, ink))),
    )
}

@Composable
internal fun Modifier.serverWash(data: ServerData, strength: Float = 0.3f): Modifier {
    val tint = rememberServerTint(data)
    return background(Brush.horizontalGradient(listOf(tint.copy(alpha = strength), Color.Transparent)))
}
