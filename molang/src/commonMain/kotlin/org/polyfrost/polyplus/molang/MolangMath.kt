package org.polyfrost.polyplus.molang

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val ABS_MASK = 0x7FFFFFFF
private const val EXP_MASK = 0x7FF00000
private const val EXP_SHIFT = 20
private const val EXP_BITS = 0x7FF
private const val LONG_EXP_SHIFT = 52
private const val WORD = 32
private const val LOW_MASK = 0xFFFFFFFFL

private const val DEGREES_TO_RADIANS = 0.017453292519943295
private const val RADIANS_TO_DEGREES = 57.29577951308232
private const val HALF = 0.5

private const val PIO4_HIGH = 0x3FE921FB
private const val PIO2_HIGH = 0x3FF921FB
private const val THREE_PIO4_HIGH = 0x4002D97C
private const val TINY_HIGH = 0x3E400000
private const val NPIO2_TABLE = 32
private const val QUADRANT_MASK = 3
private const val SECOND_ROUND = 16
private const val THIRD_ROUND = 49
private const val INV_PIO2 = 6.36619772367581382433e-01
private const val PIO2 = 1.57079632679489661923e+00
private const val PIO2_1 = 1.57079632673412561417e+00
private const val PIO2_1T = 6.07710050650619224932e-11
private const val PIO2_2 = 6.07710050630396597660e-11
private const val PIO2_2T = 2.02226624879595063154e-21
private const val PIO2_3 = 2.02226624871116645580e-21
private const val PIO2_3T = 8.47842766036889956997e-32

private const val S1 = -1.66666666666666324348e-01
private const val S2 = 8.33333333332248946124e-03
private const val S3 = -1.98412698298579493134e-04
private const val S4 = 2.75573137070700676789e-06
private const val S5 = -2.50507602534068634195e-08
private const val S6 = 1.58969099521155010221e-10

private const val C1 = 4.16666666666666019037e-02
private const val C2 = -1.38888888888741095749e-03
private const val C3 = 2.48015872894767294178e-05
private const val C4 = -2.75573143513906633035e-07
private const val C5 = 2.08757232129817482790e-09
private const val C6 = -1.13596475577881948265e-11
private const val COS_SMALL_HIGH = 0x3FD33333
private const val COS_LARGE_HIGH = 0x3FE90000
private const val COS_QUARTER = 0x00200000
private const val COS_QX = 0.28125

private const val ATAN_HUGE_HIGH = 0x44100000
private const val ATAN_SMALL_HIGH = 0x3FDC0000
private const val ATAN_TINY_HIGH = 0x3E200000
private const val ATAN_ONE_HIGH = 0x3FF30000
private const val ATAN_HALF_HIGH = 0x3FE60000
private const val ATAN_BIG_HIGH = 0x40038000
private const val ATAN_ONE_HALF = 1.5
private const val ATAN_LAST = 3
private val ATAN_HIGH = doubleArrayOf(
    4.63647609000806093515e-01,
    7.85398163397448278999e-01,
    9.82793723247329054082e-01,
    1.57079632679489655800e+00,
)
private val ATAN_LOW = doubleArrayOf(
    2.26987774529616870924e-17,
    3.06161699786838301793e-17,
    1.39033110312309984516e-17,
    6.12323399573676603587e-17,
)
private const val AT0 = 3.33333333333329318027e-01
private const val AT1 = -1.99999999998764832476e-01
private const val AT2 = 1.42857142725034663711e-01
private const val AT3 = -1.11111104054623557880e-01
private const val AT4 = 9.09088713343650656196e-02
private const val AT5 = -7.69187620504482999495e-02
private const val AT6 = 6.66107313738753120669e-02
private const val AT7 = -5.83357013379057348645e-02
private const val AT8 = 4.97687799461593236017e-02
private const val AT9 = -3.65315727442169155270e-02
private const val AT10 = 1.62858201153657823623e-02
private const val PI = 3.1415926535897931160e+00
private const val PI_LO = 1.2246467991473531772e-16

private const val EXP_LIMIT_HIGH = 0x40862E42
private const val EXP_HALF_LN2_HIGH = 0x3FD62E42
private const val EXP_ONE_HALF_LN2_HIGH = 0x3FF0A2B2
private const val EXP_TINY_HIGH = 0x3E300000
private const val EXP_OVERFLOW = 7.09782712893383973096e+02
private const val EXP_UNDERFLOW = -7.45133219101941108420e+02
private const val EXP_MIN_K = -1021
private const val EXP_SUBNORMAL_K = 1000
private const val TWO_M1000 = 9.33263618503218878990e-302
private const val LN2_HI = 6.93147180369123816490e-01
private const val LN2_LO = 1.90821492927058770002e-10
private const val INV_LN2 = 1.44269504088896338700e+00
private const val P1 = 1.66666666666666019037e-01
private const val P2 = -2.77777777770155933842e-03
private const val P3 = 6.61375632143793436117e-05
private const val P4 = -1.65339022054652515390e-06
private const val P5 = 4.13813679705723846039e-08

private const val LOG_MIN_HIGH = 0x00100000
private const val LOG_BIAS = 1023
private const val LOG_SUBNORMAL_K = 54
private const val TWO54 = 1.80143985094819840000e+16
private const val LOG_MANTISSA = 0x000FFFFF
private const val LOG_SQRT2_OFFSET = 0x95F64
private const val LOG_ONE_HIGH = 0x3FF00000
private const val LOG_SMALL_LIMIT = 3
private const val LOG_I_OFFSET = 0x6147A
private const val LOG_J_OFFSET = 0x6B851
private const val THIRD = 0.33333333333333333
private const val LG1 = 6.666666666666735130e-01
private const val LG2 = 3.999999999940941908e-01
private const val LG3 = 2.857142874366239149e-01
private const val LG4 = 2.222219843214978396e-01
private const val LG5 = 1.818357216161805012e-01
private const val LG6 = 1.531383769920937332e-01
private const val LG7 = 1.479819860511658591e-01

private const val MAX_INT_POWER = 1024.0

private const val HASH_X = 0x27D4EB2D
private const val HASH_Y = 0x165667B1
private const val HASH_SEED = 0x5BD1E995
private const val HASH_MIX_1 = 0x2C1B3C6D
private const val HASH_MIX_2 = 0x297A2D39
private const val HASH_SHIFT_1 = 15
private const val HASH_SHIFT_2 = 12
private const val HASH_BITS = 8
private const val HASH_SCALE = 16_777_216.0

private const val BAYER_MASK = 3
private const val BAYER_BIT_3 = 3
private const val BAYER_BIT_2 = 2
private const val BAYER_LEVELS = 16.0

private const val HUE_TURN = 360.0
private const val HUE_SECTOR = 60.0
private const val HUE_SECTORS = 6.0
private const val HSV_RED = 5.0
private const val HSV_GREEN = 3.0
private const val HSV_PEAK = 4.0
private const val CHANNEL_MAX = 255.0
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val SMOOTH_A = 3.0

internal object MolangMath {
    private val npio2High = IntArray(NPIO2_TABLE) { high((it + 1) * PIO2) }

    fun sinDegrees(degrees: Double): Double = sin(degrees * DEGREES_TO_RADIANS)

    fun cosDegrees(degrees: Double): Double = cos(degrees * DEGREES_TO_RADIANS)

    fun atan2Degrees(y: Double, x: Double): Double = atan2(y, x) * RADIANS_TO_DEGREES

    fun sin(x: Double): Double {
        val ix = high(x) and ABS_MASK
        return when {
            ix <= PIO4_HIGH -> kernelSin(x, 0.0, false)
            ix >= EXP_MASK -> Double.NaN
            else -> reduce(x) { n, y0, y1 ->
                when (n and QUADRANT_MASK) {
                    0 -> kernelSin(y0, y1, true)
                    1 -> kernelCos(y0, y1)
                    2 -> -kernelSin(y0, y1, true)
                    else -> -kernelCos(y0, y1)
                }
            }
        }
    }

    fun cos(x: Double): Double {
        val ix = high(x) and ABS_MASK
        return when {
            ix <= PIO4_HIGH -> kernelCos(x, 0.0)
            ix >= EXP_MASK -> Double.NaN
            else -> reduce(x) { n, y0, y1 ->
                when (n and QUADRANT_MASK) {
                    0 -> kernelCos(y0, y1)
                    1 -> -kernelSin(y0, y1, true)
                    2 -> -kernelCos(y0, y1)
                    else -> kernelSin(y0, y1, true)
                }
            }
        }
    }

    private inline fun <R> reduce(x: Double, body: (Int, Double, Double) -> R): R {
        val hx = high(x)
        val ix = hx and ABS_MASK
        if (ix < THREE_PIO4_HIGH) {
            val sign = if (hx > 0) 1.0 else -1.0
            val near = ix == PIO2_HIGH
            val z = if (near) x - sign * PIO2_1 - sign * PIO2_2 else x - sign * PIO2_1
            val tail = sign * if (near) PIO2_2T else PIO2_1T
            val y0 = z - tail
            return body(if (hx > 0) 1 else -1, y0, (z - y0) - tail)
        }
        val t = abs(x)
        val n = (t * INV_PIO2 + HALF).toInt()
        val fn = n.toDouble()
        var r = t - fn * PIO2_1
        var w = fn * PIO2_1T
        var y0 = r - w
        if (n >= NPIO2_TABLE || ix == npio2High[n - 1]) {
            val j = ix shr EXP_SHIFT
            if (j - exponent(y0) > SECOND_ROUND) {
                var u = r
                w = fn * PIO2_2
                r = u - w
                w = fn * PIO2_2T - ((u - r) - w)
                y0 = r - w
                if (j - exponent(y0) > THIRD_ROUND) {
                    u = r
                    w = fn * PIO2_3
                    r = u - w
                    w = fn * PIO2_3T - ((u - r) - w)
                    y0 = r - w
                }
            }
        }
        val y1 = (r - y0) - w
        return if (hx < 0) body(-n, -y0, -y1) else body(n, y0, y1)
    }

    private fun kernelSin(x: Double, y: Double, tail: Boolean): Double {
        if ((high(x) and ABS_MASK) < TINY_HIGH) return x
        val z = x * x
        val v = z * x
        val r = S2 + z * (S3 + z * (S4 + z * (S5 + z * S6)))
        return if (tail) x - ((z * (HALF * y - v * r) - y) - v * S1) else x + v * (S1 + z * r)
    }

    private fun kernelCos(x: Double, y: Double): Double {
        val ix = high(x) and ABS_MASK
        if (ix < TINY_HIGH) return 1.0
        val z = x * x
        val r = z * (C1 + z * (C2 + z * (C3 + z * (C4 + z * (C5 + z * C6)))))
        if (ix < COS_SMALL_HIGH) return 1.0 - (HALF * z - (z * r - x * y))
        val qx = if (ix > COS_LARGE_HIGH) COS_QX else fromHigh(ix - COS_QUARTER)
        val hz = HALF * z - qx
        return (1.0 - qx) - (hz - (z * r - x * y))
    }

    fun atan(value: Double): Double {
        val hx = high(value)
        val ix = hx and ABS_MASK
        if (ix >= ATAN_HUGE_HIGH) {
            return when {
                value.isNaN() -> value
                hx > 0 -> ATAN_HIGH[ATAN_LAST] + ATAN_LOW[ATAN_LAST]
                else -> -ATAN_HIGH[ATAN_LAST] - ATAN_LOW[ATAN_LAST]
            }
        }
        if (ix < ATAN_SMALL_HIGH) return if (ix < ATAN_TINY_HIGH) value else value - value * atanSeries(value)
        val a = abs(value)
        val id = when {
            ix < ATAN_HALF_HIGH -> 0
            ix < ATAN_ONE_HIGH -> 1
            ix < ATAN_BIG_HIGH -> 2
            else -> ATAN_LAST
        }
        val x = when (id) {
            0 -> (2.0 * a - 1.0) / (2.0 + a)
            1 -> (a - 1.0) / (a + 1.0)
            2 -> (a - ATAN_ONE_HALF) / (1.0 + ATAN_ONE_HALF * a)
            else -> -1.0 / a
        }
        val z = ATAN_HIGH[id] - ((x * atanSeries(x) - ATAN_LOW[id]) - x)
        return if (hx < 0) -z else z
    }

    private fun atanSeries(x: Double): Double {
        val z = x * x
        val w = z * z
        val s1 = z * (AT0 + w * (AT2 + w * (AT4 + w * (AT6 + w * (AT8 + w * AT10)))))
        val s2 = w * (AT1 + w * (AT3 + w * (AT5 + w * (AT7 + w * AT9))))
        return s1 + s2
    }

    fun atan2(y: Double, x: Double): Double {
        val negativeX = x < 0.0 || (x == 0.0 && 1.0 / x < 0.0)
        return when {
            x.isNaN() || y.isNaN() -> Double.NaN
            x == 1.0 -> atan(y)
            y == 0.0 -> if (negativeX) PI.withSignOf(y) else y
            x == 0.0 -> (PI * HALF).withSignOf(y)
            else -> {
                val z = atan(abs(y / x))
                when {
                    !negativeX -> z.withSignOf(y)
                    y > 0.0 -> PI - (z - PI_LO)
                    else -> (z - PI_LO) - PI
                }
            }
        }
    }

    private fun Double.withSignOf(sign: Double): Double = if (sign < 0.0 || (sign == 0.0 && 1.0 / sign < 0.0)) -this else this

    fun exp(value: Double): Double {
        val hx = high(value)
        val negative = hx < 0
        val ix = hx and ABS_MASK
        if (ix >= EXP_LIMIT_HIGH) {
            return when {
                value.isNaN() -> value
                value > EXP_OVERFLOW -> Double.POSITIVE_INFINITY
                value < EXP_UNDERFLOW -> 0.0
                else -> expReduced(value, ix, negative)
            }
        }
        return when {
            ix > EXP_HALF_LN2_HIGH -> expReduced(value, ix, negative)
            ix < EXP_TINY_HIGH -> 1.0 + value
            else -> expSmall(value)
        }
    }

    private fun expSmall(x: Double): Double {
        val t = x * x
        val c = x - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))))
        return 1.0 - ((x * c) / (c - 2.0) - x)
    }

    private fun expReduced(value: Double, ix: Int, negative: Boolean): Double {
        val k: Int
        val hi: Double
        val lo: Double
        if (ix < EXP_ONE_HALF_LN2_HIGH) {
            hi = if (negative) value + LN2_HI else value - LN2_HI
            lo = if (negative) -LN2_LO else LN2_LO
            k = if (negative) -1 else 1
        } else {
            k = (INV_LN2 * value + if (negative) -HALF else HALF).toInt()
            val t = k.toDouble()
            hi = value - t * LN2_HI
            lo = t * LN2_LO
        }
        val x = hi - lo
        val t = x * x
        val c = x - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))))
        val y = 1.0 - ((lo - (x * c) / (2.0 - c)) - hi)
        return if (k >= EXP_MIN_K) scale(y, k) else scale(y, k + EXP_SUBNORMAL_K) * TWO_M1000
    }

    fun log(value: Double): Double {
        var x = value
        var hx = high(x)
        var k = 0
        if (hx < LOG_MIN_HIGH) {
            if (x == 0.0) return Double.NEGATIVE_INFINITY
            if (hx < 0) return Double.NaN
            k -= LOG_SUBNORMAL_K
            x *= TWO54
            hx = high(x)
        }
        if (hx >= EXP_MASK) return x + x
        k += (hx shr EXP_SHIFT) - LOG_BIAS
        hx = hx and LOG_MANTISSA
        val i = (hx + LOG_SQRT2_OFFSET) and LOG_MIN_HIGH
        x = Double.fromBits(((hx or (i xor LOG_ONE_HIGH)).toLong() shl WORD) or (x.toRawBits() and LOW_MASK))
        k += i shr EXP_SHIFT
        return logReduced(x - 1.0, k, hx)
    }

    private fun logReduced(f: Double, k: Int, hx: Int): Double {
        val dk = k.toDouble()
        if ((LOG_MANTISSA and (2 + hx)) < LOG_SMALL_LIMIT) {
            if (f == 0.0) return if (k == 0) 0.0 else dk * LN2_HI + dk * LN2_LO
            val r = f * f * (HALF - THIRD * f)
            return if (k == 0) f - r else dk * LN2_HI - ((r - dk * LN2_LO) - f)
        }
        val s = f / (2.0 + f)
        val z = s * s
        val w = z * z
        val t1 = w * (LG2 + w * (LG4 + w * LG6))
        val t2 = z * (LG1 + w * (LG3 + w * (LG5 + w * LG7)))
        val r = t2 + t1
        if (((hx - LOG_I_OFFSET) or (LOG_J_OFFSET - hx)) > 0) {
            val hfsq = HALF * f * f
            return if (k == 0) f - (hfsq - s * (hfsq + r)) else dk * LN2_HI - ((hfsq - (s * (hfsq + r) + dk * LN2_LO)) - f)
        }
        return if (k == 0) f - s * (f - r) else dk * LN2_HI - ((s * (f - r) - dk * LN2_LO) - f)
    }

    fun pow(x: Double, y: Double): Double = when {
        y == 0.0 -> 1.0
        x.isNaN() || y.isNaN() -> Double.NaN
        y == floor(y) && abs(y) <= MAX_INT_POWER -> integerPower(x, y.toInt())
        x < 0.0 -> Double.NaN
        x == 0.0 -> if (y > 0.0) 0.0 else Double.POSITIVE_INFINITY
        else -> exp(y * log(x))
    }

    private fun integerPower(x: Double, n: Int): Double {
        var result = 1.0
        var base = x
        var e = abs(n)
        while (e > 0) {
            if (e and 1 == 1) {
                result *= base
            }
            base *= base
            e = e shr 1
        }
        return if (n < 0) 1.0 / result else result
    }

    fun mod(a: Double, b: Double): Double = if (b == 0.0) 0.0 else a % b

    fun float(x: Double): Double = Float.fromBits(x.toFloat().toRawBits()).toDouble()

    fun floorMod(a: Double, b: Double): Double {
        if (b == 0.0) return 0.0
        val m = a % b
        return if (m != 0.0 && (m < 0.0) != (b < 0.0)) m + b else m
    }

    fun round(x: Double): Double {
        val f = floor(x)
        return if (x - f >= HALF) f + 1.0 else f
    }

    fun frac(x: Double): Double = x - floor(x)

    fun sign(x: Double): Double = when {
        x > 0.0 -> 1.0
        x < 0.0 -> -1.0
        else -> 0.0
    }

    fun step(edge: Double, x: Double): Double = if (x >= edge) 1.0 else 0.0

    fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        if (edge0 == edge1) return step(edge0, x)
        return smooth(clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0))
    }

    fun clamp(value: Double, low: Double, high: Double): Double = min(max(value, low), high)

    fun cell(value: Double): Int = floor(value).toInt()

    fun hash(x: Int, y: Int, seed: Int): Double {
        var h = (x * HASH_X) xor (y * HASH_Y) xor (seed * HASH_SEED)
        h = h xor (h ushr HASH_SHIFT_1)
        h *= HASH_MIX_1
        h = h xor (h ushr HASH_SHIFT_2)
        h *= HASH_MIX_2
        h = h xor (h ushr HASH_SHIFT_1)
        return (h ushr HASH_BITS).toDouble() / HASH_SCALE
    }

    fun noise(x: Double, y: Double, seed: Int): Double {
        val x0 = cell(x)
        val y0 = cell(y)
        val fx = smooth(x - x0)
        val fy = smooth(y - y0)
        val top = lerp(fx, hash(x0, y0, seed), hash(x0 + 1, y0, seed))
        val bottom = lerp(fx, hash(x0, y0 + 1, seed), hash(x0 + 1, y0 + 1, seed))
        return lerp(fy, top, bottom)
    }

    fun fbm(x: Double, y: Double, octaves: Int, seed: Int): Double {
        var px = x
        var py = y
        var sum = 0.0
        var weight = 1.0
        var total = 0.0
        for (i in 0 until octaves) {
            sum += noise(px, py, seed + i) * weight
            total += weight
            px *= 2.0
            py *= 2.0
            weight /= 2.0
        }
        return sum / total
    }

    fun octaves(value: Double): Int = clamp(floor(value), 1.0, MolangLimits.MAX_OCTAVES.toDouble()).toInt()

    fun bayer(x: Int, y: Int): Double {
        val a = (x xor y) and BAYER_MASK
        val b = y and BAYER_MASK
        val level = ((a and 1) shl BAYER_BIT_3) or ((b and 1) shl BAYER_BIT_2) or
            ((a shr 1) shl 1) or (b shr 1)
        return level / BAYER_LEVELS
    }

    fun hsv(hue: Double, saturation: Double, value: Double): Double {
        val h = floorMod(hue, HUE_TURN) / HUE_SECTOR
        val s = clamp(saturation, 0.0, 1.0)
        val v = clamp(value, 0.0, 1.0)
        val red = hsvChannel(HSV_RED, h, s, v)
        val green = hsvChannel(HSV_GREEN, h, s, v)
        val blue = hsvChannel(1.0, h, s, v)
        return ((red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue).toDouble()
    }

    private fun hsvChannel(n: Double, h: Double, s: Double, v: Double): Int {
        val k = (n + h) % HUE_SECTORS
        val c = v - v * s * max(0.0, min(min(k, HSV_PEAK - k), 1.0))
        return round(c * CHANNEL_MAX).toInt()
    }

    private fun smooth(t: Double): Double = t * t * (SMOOTH_A - 2.0 * t)

    private fun lerp(t: Double, a: Double, b: Double): Double = a + (b - a) * t

    private fun scale(y: Double, k: Int): Double = Double.fromBits(y.toRawBits() + (k.toLong() shl LONG_EXP_SHIFT))

    private fun exponent(x: Double): Int = (high(x) shr EXP_SHIFT) and EXP_BITS

    private fun fromHigh(word: Int): Double = Double.fromBits(word.toLong() shl WORD)
}

private fun high(x: Double): Int = (x.toRawBits() ushr WORD).toInt()
