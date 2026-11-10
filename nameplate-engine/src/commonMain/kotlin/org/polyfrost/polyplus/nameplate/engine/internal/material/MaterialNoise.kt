package org.polyfrost.polyplus.nameplate.engine.internal.material

import kotlin.math.floor

internal object MaterialNoise {
    private const val X_PRIME = 0x27D4EB2D
    private const val Y_PRIME = 0x165667B1
    private const val SEED_PRIME = 0x5BD1E995
    private const val MIX_1 = 0x2C1B3C6D
    private const val MIX_2 = 0x297A2D39
    private const val SHIFT_1 = 15
    private const val SHIFT_2 = 12
    private const val FRACTION_SHIFT = 8
    private const val FRACTION_SCALE = 16777216.0
    private const val SMOOTH = 3.0
    private const val BAYER_SIZE = 4
    private const val BAYER_LEVELS = 16.0
    private const val HALF = 0.5

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun hash(x: Int, y: Int, seed: Int): Double = finish(x * X_PRIME xor y * Y_PRIME xor seed * SEED_PRIME)

    private fun finish(start: Int): Double {
        var h = start
        h = h xor (h ushr SHIFT_1)
        h *= MIX_1
        h = h xor (h ushr SHIFT_2)
        h *= MIX_2
        h = h xor (h ushr SHIFT_1)
        return (h ushr FRACTION_SHIFT).toDouble() / FRACTION_SCALE
    }

    fun wrap(x: Int, period: Int): Int = if (period <= 0) x else x.mod(period)

    fun noise(x: Double, y: Double, periodX: Int, periodY: Int, seed: Int): Double {
        val cellX = floor(x)
        val cellY = floor(y)
        val fx = smooth(x - cellX)
        val fy = smooth(y - cellY)
        val ix = cellX.toInt()
        val iy = cellY.toInt()
        val x0 = wrap(ix, periodX)
        val y0 = wrap(iy, periodY)
        val left = x0 * X_PRIME
        val right = following(ix, x0, periodX) * X_PRIME
        val salt = seed * SEED_PRIME
        val upper = y0 * Y_PRIME xor salt
        val lower = following(iy, y0, periodY) * Y_PRIME xor salt
        val top = mix(finish(left xor upper), finish(right xor upper), fx)
        val bottom = mix(finish(left xor lower), finish(right xor lower), fx)
        return mix(top, bottom, fy)
    }

    private fun following(cell: Int, wrapped: Int, period: Int): Int = when {
        period <= 0 || cell == Int.MAX_VALUE -> wrap(cell + 1, period)
        wrapped + 1 == period -> 0
        else -> wrapped + 1
    }

    fun fbm(x: Double, y: Double, settings: NodeSettings): Double {
        var sum = 0.0
        var weight = 1.0
        var total = 0.0
        var px = x
        var py = y
        var tileX = settings.periodX
        var tileY = settings.periodY
        for (octave in 0 until settings.octaves) {
            sum += noise(px, py, tileX, tileY, settings.seed + octave) * weight
            total += weight
            px *= 2.0
            py *= 2.0
            tileX *= 2
            tileY *= 2
            weight *= HALF
        }
        return sum / total
    }

    fun bayer(x: Double, y: Double): Double =
        BAYER[wrap(floor(y).toInt(), BAYER_SIZE) * BAYER_SIZE + wrap(floor(x).toInt(), BAYER_SIZE)] / BAYER_LEVELS

    fun mix(a: Double, b: Double, t: Double): Double = a * (1.0 - t) + b * t

    private fun smooth(f: Double): Double = f * f * (SMOOTH - 2.0 * f)
}
