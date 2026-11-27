package org.polyfrost.polyplus.nameplate.engine.internal.color

import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.sin

internal object Oklab {
    private const val GAMUT_EPSILON = 1.0e-4
    private const val GAMUT_STEPS = 12

    fun lightness(rgb: Int): Double =
        0.2104542553 * lPrime(rgb) + 0.7936177850 * mPrime(rgb) - 0.0040720468 * sPrime(rgb)

    fun a(rgb: Int): Double =
        1.9779984951 * lPrime(rgb) - 2.4285922050 * mPrime(rgb) + 0.4505937099 * sPrime(rgb)

    fun b(rgb: Int): Double =
        0.0259040371 * lPrime(rgb) + 0.7827717662 * mPrime(rgb) - 0.8086757660 * sPrime(rgb)

    fun toRgb(lightness: Double, a: Double, b: Double): Int =
        (Srgb.encode8(linear(lightness, a, b, 0)) shl 16) or
            (Srgb.encode8(linear(lightness, a, b, 1)) shl 8) or
            Srgb.encode8(linear(lightness, a, b, 2))

    fun toRgbKeepingHue(lightness: Double, chroma: Double, hue: Double): Int {
        val cos = cos(hue)
        val sin = sin(hue)
        if (inGamut(lightness, chroma * cos, chroma * sin)) return toRgb(lightness, chroma * cos, chroma * sin)

        var low = 0.0
        var high = chroma
        repeat(GAMUT_STEPS) {
            val mid = (low + high) * 0.5
            if (inGamut(lightness, mid * cos, mid * sin)) {
                low = mid
            } else {
                high = mid
            }
        }

        return toRgb(lightness, low * cos, low * sin)
    }

    private fun inGamut(lightness: Double, a: Double, b: Double): Boolean {
        for (channel in 0 until 3) {
            val value = linear(lightness, a, b, channel)
            if (value < -GAMUT_EPSILON || value > 1.0 + GAMUT_EPSILON) return false
        }

        return true
    }

    private fun lPrime(rgb: Int): Double =
        cbrt(0.4122214708 * red(rgb) + 0.5363325363 * green(rgb) + 0.0514459929 * blue(rgb))

    private fun mPrime(rgb: Int): Double =
        cbrt(0.2119034982 * red(rgb) + 0.6806995451 * green(rgb) + 0.1073969566 * blue(rgb))

    private fun sPrime(rgb: Int): Double =
        cbrt(0.0883024619 * red(rgb) + 0.2817188376 * green(rgb) + 0.6299787005 * blue(rgb))

    private fun red(rgb: Int): Double = Srgb.linear((rgb shr 16) and 0xFF)

    private fun green(rgb: Int): Double = Srgb.linear((rgb shr 8) and 0xFF)

    private fun blue(rgb: Int): Double = Srgb.linear(rgb and 0xFF)

    private fun linear(lightness: Double, a: Double, b: Double, channel: Int): Double {
        var l = lightness + 0.3963377774 * a + 0.2158037573 * b
        var m = lightness - 0.1055613458 * a - 0.0638541728 * b
        var s = lightness - 0.0894841775 * a - 1.2914855480 * b
        l *= l * l
        m *= m * m
        s *= s * s
        return when (channel) {
            0 -> 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
            1 -> -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
            else -> -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        }
    }
}
