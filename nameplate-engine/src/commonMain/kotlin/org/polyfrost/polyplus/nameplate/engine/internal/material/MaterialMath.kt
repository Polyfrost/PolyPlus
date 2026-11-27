package org.polyfrost.polyplus.nameplate.engine.internal.material

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

internal object Trig {
    private const val PI = 3.141592653589793
    private const val HALF_PI = 1.5707963267948966
    private const val TWO_PI = 6.283185307179586
    private const val TWO_PI_LOW = 2.4492935982947064E-16
    private const val ATAN_TERMS = 24
    private const val HALF = 0.5

    private val SIN = DoubleArray(SIN_TERMS) { term ->
        var factorial = 1.0
        for (k in 2..(2 * term + 1)) {
            factorial *= k
        }
        (if (term % 2 == 0) 1.0 else -1.0) / factorial
    }

    private val ATAN = DoubleArray(ATAN_TERMS) { term -> (if (term % 2 == 0) 1.0 else -1.0) / (2 * term + 1) }

    fun sin(x: Double): Double {
        if (!x.isFinite()) return Double.NaN
        val turns = floor(x / TWO_PI + HALF)
        var r = (x - turns * TWO_PI) - turns * TWO_PI_LOW
        if (r > HALF_PI) {
            r = PI - r
        } else if (r < -HALF_PI) {
            r = -PI - r
        }
        val r2 = r * r
        var sum = 0.0
        for (term in SIN.indices.reversed()) {
            sum = sum * r2 + SIN[term]
        }
        return r * sum
    }

    fun cos(x: Double): Double = sin(x + HALF_PI)

    fun atan(x: Double): Double {
        val a = abs(x)
        val inverted = a > 1.0
        var t = if (inverted) 1.0 / a else a
        t /= 1.0 + sqrt(1.0 + t * t)
        val t2 = t * t
        var sum = 0.0
        for (term in ATAN.indices.reversed()) {
            sum = sum * t2 + ATAN[term]
        }
        var result = 2.0 * t * sum
        if (inverted) {
            result = HALF_PI - result
        }
        return if (x < 0.0) -result else result
    }

    fun atan2(y: Double, x: Double): Double = when {
        x > 0.0 -> atan(y / x)
        x < 0.0 -> atan(y / x) + if (y >= 0.0) PI else -PI
        y > 0.0 -> HALF_PI
        y < 0.0 -> -HALF_PI
        else -> 0.0
    }
}

private const val SIN_TERMS = 9

internal object Exponent {
    private const val LN2_HIGH = 6.93147180369123816490E-01
    private const val LN2_LOW = 1.90821492927058770002E-10
    private const val LN2 = 0.6931471805599453
    private const val SQRT2 = 1.4142135623730951
    private const val MAX_EXP = 709.0
    private const val MIN_EXP = -745.0
    private const val EXP_TERMS = 15
    private const val LOG_TERMS = 13
    private const val BIAS = 1023
    private const val MANTISSA_BITS = 52
    private const val EXPONENT_MASK = 0x7FF
    private const val MANTISSA_MASK = 0xFFFFFFFFFFFFFL
    private const val ONE_BITS = 0x3FF0000000000000L
    private const val SUBNORMAL_SCALE = 18014398509481984.0
    private const val SUBNORMAL_SHIFT = 54
    private const val LOW_SPLIT = -1000
    private const val MAX_TWO = 1023
    private const val MIN_NORMAL = 2.2250738585072014E-308
    private const val HALF = 0.5

    private val EXP = DoubleArray(EXP_TERMS) { term ->
        var factorial = 1.0
        for (k in 2..term) {
            factorial *= k
        }
        1.0 / factorial
    }

    private val LOG = DoubleArray(LOG_TERMS) { term -> 1.0 / (2 * term + 1) }

    fun exp(x: Double): Double = when {
        x.isNaN() -> x
        x > MAX_EXP -> Double.POSITIVE_INFINITY
        x < MIN_EXP -> 0.0
        else -> {
            val k = floor(x / LN2 + HALF)
            val r = (x - k * LN2_HIGH) - k * LN2_LOW
            var sum = 0.0
            for (term in EXP.indices.reversed()) {
                sum = sum * r + EXP[term]
            }
            scale(sum, k.toInt())
        }
    }

    fun ln(x: Double): Double {
        if (x.isNaN() || x <= 0.0 || x == Double.POSITIVE_INFINITY) return if (x == 0.0) Double.NEGATIVE_INFINITY else Double.NaN
        val normal = if (x < MIN_NORMAL) x * SUBNORMAL_SCALE else x
        val bits = normal.toRawBits()
        var exponent = ((bits ushr MANTISSA_BITS).toInt() and EXPONENT_MASK) - BIAS
        if (normal != x) {
            exponent -= SUBNORMAL_SHIFT
        }
        var m = Double.fromBits((bits and MANTISSA_MASK) or ONE_BITS)
        if (m > SQRT2) {
            m /= 2.0
            exponent++
        }
        val s = (m - 1.0) / (m + 1.0)
        val s2 = s * s
        var sum = 0.0
        for (term in LOG.indices.reversed()) {
            sum = sum * s2 + LOG[term]
        }
        return exponent * LN2_HIGH + (exponent * LN2_LOW + 2.0 * s * sum)
    }

    fun pow(base: Double, power: Double): Double = if (base > 0.0) exp(power * ln(base)) else 0.0

    private fun scale(value: Double, k: Int): Double =
        if (k < LOW_SPLIT) value * twoTo(k - LOW_SPLIT) * twoTo(LOW_SPLIT) else value * twoTo(k)

    private val TWO_TO = DoubleArray(MAX_TWO - LOW_SPLIT + 1) {
        Double.fromBits((it + LOW_SPLIT + BIAS).toLong() shl MANTISSA_BITS)
    }

    private fun twoTo(k: Int): Double = TWO_TO[k - LOW_SPLIT]
}
