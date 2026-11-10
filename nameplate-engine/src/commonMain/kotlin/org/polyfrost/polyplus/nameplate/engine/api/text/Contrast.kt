package org.polyfrost.polyplus.nameplate.engine.api.text

import org.polyfrost.polyplus.nameplate.engine.internal.color.Oklab
import org.polyfrost.polyplus.nameplate.engine.internal.color.Srgb
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

object Contrast {
    const val WHITE = 0xFFFFFF
    internal const val NEAR_BLACK = 0x1A1A1A
    internal const val READABLE = 4.5
    internal const val LARGE_TEXT = 3.0

    private const val MAX_RATIO = 21.0
    private const val LIGHTNESS_SAMPLES = 256
    private const val REFINE_STEPS = 10

    internal val DEFAULT_CANDIDATES: IntArray
        get() = intArrayOf(WHITE, NEAR_BLACK)

    enum class Decoration {
        NONE,
        SHADOW,
        OUTLINE,
    }

    data class Choice(val color: Int, val minRatio: Double, val shadow: Int, val decoration: Decoration)

    data class Adjusted(val color: Int, val minRatio: Double, val decoration: Decoration)

    fun luminance(rgb: Int): Double =
        0.2126 * Srgb.linear((rgb shr 16) and 0xFF) +
            0.7152 * Srgb.linear((rgb shr 8) and 0xFF) +
            0.0722 * Srgb.linear(rgb and 0xFF)

    internal fun ratio(first: Int, second: Int): Double = ratioOf(luminance(first), luminance(second))

    internal fun compositeOver(argb: Int, backdropRgb: Int): Int {
        val alpha = argb ushr 24
        val inverse = 255 - alpha
        val r = blend((argb shr 16) and 0xFF, (backdropRgb shr 16) and 0xFF, alpha, inverse)
        val g = blend((argb shr 8) and 0xFF, (backdropRgb shr 8) and 0xFF, alpha, inverse)
        val b = blend(argb and 0xFF, backdropRgb and 0xFF, alpha, inverse)
        return (r shl 16) or (g shl 8) or b
    }

    fun choose(pixels: IntArray, count: Int, candidates: IntArray): Choice {
        checkCount(pixels, count)
        require(candidates.isNotEmpty()) { "no candidates" }

        if (count == 0) return Choice(candidates[0], MAX_RATIO, opposite(candidates[0], candidates), Decoration.NONE)

        var best = candidates[0]
        var bestRatio = minRatio(best, pixels, count)
        for (i in 1 until candidates.size) {
            val score = minRatio(candidates[i], pixels, count)
            if (score > bestRatio) {
                best = candidates[i]
                bestRatio = score
            }
        }

        return Choice(best, bestRatio, opposite(best, candidates), decorationFor(bestRatio))
    }

    fun opposite(rgb: Int, candidates: IntArray): Int {
        val luminance = luminance(rgb)
        var best = candidates[0]
        var bestRatio = ratioOf(luminance, luminance(best))
        for (i in 1 until candidates.size) {
            val score = ratioOf(luminance, luminance(candidates[i]))
            if (score > bestRatio) {
                best = candidates[i]
                bestRatio = score
            }
        }

        return best
    }

    fun adjust(rgb: Int, pixels: IntArray, count: Int): Adjusted {
        checkCount(pixels, count)
        val original = rgb and 0xFFFFFF
        if (count == 0) return Adjusted(original, MAX_RATIO, Decoration.NONE)

        val originalRatio = minRatio(original, pixels, count)
        if (originalRatio >= READABLE) return Adjusted(original, originalRatio, Decoration.NONE)

        val a = Oklab.a(original)
        val b = Oklab.b(original)
        val chroma = hypot(a, b)
        val hue = atan2(b, a)
        val origin = Oklab.lightness(original)
        val step = 1.0 / LIGHTNESS_SAMPLES

        var feasible = false
        var feasibleLightness = 0.0
        var feasibleColor = original
        var feasibleDistance = Double.MAX_VALUE
        var bestRatio = originalRatio
        var bestColor = original
        var bestDistance = 0.0
        for (i in 0..LIGHTNESS_SAMPLES) {
            val lightness = i * step
            val color = Oklab.toRgbKeepingHue(lightness, chroma, hue)
            val score = minRatio(color, pixels, count, bestRatio)
            val distance = abs(lightness - origin)
            if (score >= READABLE) {
                if (distance < feasibleDistance) {
                    feasible = true
                    feasibleLightness = lightness
                    feasibleColor = color
                    feasibleDistance = distance
                }
            } else if (score > bestRatio || score == bestRatio && distance < bestDistance) {
                bestRatio = score
                bestColor = color
                bestDistance = distance
            }
        }

        if (!feasible) return Adjusted(bestColor, bestRatio, decorationFor(bestRatio))

        var high = feasibleLightness
        var highColor = feasibleColor
        var low = if (high > origin) maxOf(origin, high - step) else minOf(origin, high + step)
        repeat(REFINE_STEPS) {
            val mid = (low + high) * 0.5
            val color = Oklab.toRgbKeepingHue(mid, chroma, hue)
            if (minRatio(color, pixels, count) >= READABLE) {
                high = mid
                highColor = color
            } else {
                low = mid
            }
        }

        return Adjusted(highColor, minRatio(highColor, pixels, count), Decoration.NONE)
    }

    internal fun decorationFor(minRatio: Double): Decoration = when {
        minRatio >= READABLE -> Decoration.NONE
        minRatio >= LARGE_TEXT -> Decoration.SHADOW
        else -> Decoration.OUTLINE
    }

    private fun minRatio(color: Int, pixels: IntArray, count: Int, floor: Double = 0.0): Double {
        val luminance = luminance(color)
        var min = MAX_RATIO
        for (i in 0 until count) {
            val score = ratioOf(luminance, luminance(pixels[i]))
            if (score < min) {
                min = score
                if (min < floor) return min
            }
        }

        return min
    }

    private fun ratioOf(a: Double, b: Double): Double = if (a >= b) (a + 0.05) / (b + 0.05) else (b + 0.05) / (a + 0.05)

    private fun blend(source: Int, backdrop: Int, alpha: Int, inverse: Int): Int =
        (source * alpha + backdrop * inverse + 127) / 255

    private fun checkCount(pixels: IntArray, count: Int) {
        require(count >= 0 && count <= pixels.size) { "count $count outside 0..${pixels.size}" }
    }
}
