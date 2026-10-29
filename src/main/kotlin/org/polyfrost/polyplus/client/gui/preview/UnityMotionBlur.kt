package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import org.polyfrost.compose.backend.drawPoly
import org.polyfrost.compose.render.FilterQuality
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object UnityMotionBlur {
    private const val MAX_BLUR = 0.08f
    private const val JITTER = 1.0f
    private const val MIN_SAMPLES = 4f

    private const val MAX_SAMPLES = 16f

    private const val SAMPLE_LIMIT = 32

    val NONE = Motion(Offset.Zero, MIN_SAMPLES)

    private val cache = WeakHashMap<PolyBitmap, MutableMap<List<Int>, PolyBitmap>>()

    fun maxSmear(strength: Int): Motion {
        val intensity = (strength.coerceIn(0, 10) / 10f) * MAX_BLUR
        if (intensity <= 1e-6f) return NONE
        val samples = (MIN_SAMPLES + (intensity / MAX_BLUR) * (MAX_SAMPLES - MIN_SAMPLES))
            .coerceIn(MIN_SAMPLES, MAX_SAMPLES)
        return Motion(Offset(intensity, 0f), samples)
    }

    fun draw(scope: DrawScope, image: PolyBitmap, src: Rect, dst: Rect, bounds: Rect, motion: Motion) {
        val dx = motion.velocity.x * bounds.width * src.width / dst.width
        val dy = motion.velocity.y * bounds.height * src.height / dst.height
        blit(scope, blurred(image, dx, dy, motion.samples.toInt()), src, dst, bounds)
    }

    private fun blit(scope: DrawScope, image: PolyBitmap, src: Rect, dst: Rect, bounds: Rect) {
        val w = image.width.toFloat()
        val h = image.height.toFloat()
        scope.clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
            drawPoly {
                it.image(image, dst.left, dst.top, dst.width, dst.height, src.left / w, src.top / h, src.right / w, src.bottom / h, PolyColor(-1), FilterQuality.Low)
            }
        }
    }

    private fun blurred(image: PolyBitmap, dx: Float, dy: Float, n: Int): PolyBitmap {
        if (n < 2 || dx * dx + dy * dy < 1e-6f) return image
        val key = listOf((dx * 10f).roundToInt(), (dy * 10f).roundToInt(), n)
        return synchronized(cache) { cache.getOrPut(image) { HashMap() }.getOrPut(key) { smear(image, dx, dy, n.coerceAtMost(SAMPLE_LIMIT)) } }
    }

    private fun gnoise(x: Float, y: Float): Float {
        val d = x * 0.06711056f + y * 0.00583715f
        val f = 52.9829189f * (d - floor(d))
        return f - floor(f)
    }

    private fun smear(image: PolyBitmap, dx: Float, dy: Float, n: Int): PolyBitmap {
        val w = image.width
        val h = image.height
        val px = image.pixels
        val premul = FloatArray(w * h * 4)
        for (i in 0 until w * h) {
            val a = (px[i * 4 + 3].toInt() and 0xFF) / 255f
            for (c in 0..2) premul[i * 4 + c] = (px[i * 4 + c].toInt() and 0xFF) * a
            premul[i * 4 + 3] = a * 255f
        }
        val out = ByteArray(px.size)
        val acc = FloatArray(4)
        for (y in 0 until h) for (x in 0 until w) {
            val j = (gnoise(x + 0.5f, y + 0.5f) - 0.5f) * JITTER
            acc.fill(0f)
            var total = 0f
            for (i in 0 until n) {
                val t = (i + 0.5f + j) / n - 0.5f
                val weight = 1f - abs(t) * 2f
                sample(premul, w, h, x + dx * t, y + dy * t, weight, acc)
                total += weight
            }
            if (total <= 0f) continue
            val a = acc[3] / total
            if (a <= 0f) continue
            val o = (y * w + x) * 4
            for (c in 0..2) out[o + c] = (acc[c] / total / (a / 255f)).roundToInt().coerceIn(0, 255).toByte()
            out[o + 3] = a.roundToInt().coerceIn(0, 255).toByte()
        }
        return PolyBitmap(w, h, out)
    }

    private fun sample(src: FloatArray, w: Int, h: Int, x: Float, y: Float, weight: Float, acc: FloatArray) {
        val fx = x.coerceIn(0f, (w - 1).toFloat())
        val fy = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = minOf(x0 + 1, w - 1)
        val y1 = minOf(y0 + 1, h - 1)
        val tx = fx - x0
        val ty = fy - y0
        for (c in 0..3) {
            val top = src[(y0 * w + x0) * 4 + c] * (1f - tx) + src[(y0 * w + x1) * 4 + c] * tx
            val bottom = src[(y1 * w + x0) * 4 + c] * (1f - tx) + src[(y1 * w + x1) * 4 + c] * tx
            acc[c] += (top * (1f - ty) + bottom * ty) * weight
        }
    }

    data class Motion(val velocity: Offset, val samples: Float)
}
