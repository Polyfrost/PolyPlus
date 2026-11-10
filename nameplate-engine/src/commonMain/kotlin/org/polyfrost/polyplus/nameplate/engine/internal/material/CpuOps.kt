package org.polyfrost.polyplus.nameplate.engine.internal.material

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal object ColorBytes {
    private const val ALPHA = 24
    private const val RED = 16
    private const val GREEN = 8
    private const val BYTE = 0xFF
    private const val SCALE = 255.0
    private const val HALF = 0.5
    private const val ALPHA_INDEX = 3

    fun unpack(argb: Int, registers: DoubleArray, at: IntArray, first: Int, lane: Int) {
        registers[at[first] + lane] = ((argb ushr RED) and BYTE) / SCALE
        registers[at[first + 1] + lane] = ((argb ushr GREEN) and BYTE) / SCALE
        registers[at[first + 2] + lane] = (argb and BYTE) / SCALE
        registers[at[first + ALPHA_INDEX] + lane] = ((argb ushr ALPHA) and BYTE) / SCALE
    }

    fun pack(red: Double, green: Double, blue: Double, alpha: Double): Int =
        (byte(alpha) shl ALPHA) or (byte(red) shl RED) or (byte(green) shl GREEN) or byte(blue)

    fun byte(value: Double): Int {
        val scaled = floor(value * SCALE + HALF)
        return when {
            scaled.isNaN() || scaled <= 0.0 -> 0
            scaled >= SCALE -> BYTE
            else -> scaled.toInt()
        }
    }
}

internal inline fun lanes(registers: DoubleArray, count: Int, at: IntArray, op: (Double) -> Double) {
    val a = at[0]
    val o = at[1]
    for (i in 0 until count) {
        registers[o + i] = op(registers[a + i])
    }
}

internal inline fun lanes(registers: DoubleArray, count: Int, at: IntArray, op: (Double, Double) -> Double) {
    val a = at[0]
    val b = at[1]
    val o = at[2]
    for (i in 0 until count) {
        registers[o + i] = op(registers[a + i], registers[b + i])
    }
}

internal inline fun lanes(registers: DoubleArray, count: Int, at: IntArray, op: (Double, Double, Double) -> Double) {
    val a = at[0]
    val b = at[1]
    val c = at[2]
    val o = at[3]
    for (i in 0 until count) {
        registers[o + i] = op(registers[a + i], registers[b + i], registers[c + i])
    }
}

internal abstract class UnaryLane(a: Int, out: Int, private val tag: String) : CpuOp(intArrayOf(a), intArrayOf(out)) {
    override val params: String
        get() = tag
}

internal abstract class BinaryLane(a: Int, b: Int, out: Int, private val tag: String) :
    CpuOp(intArrayOf(a, b), intArrayOf(out)) {
    override val params: String
        get() = tag
}

internal abstract class TernaryLane(a: Int, b: Int, c: Int, out: Int, private val tag: String) :
    CpuOp(intArrayOf(a, b, c), intArrayOf(out)) {
    override val params: String
        get() = tag
}

internal class AbsLane(a: Int, out: Int) : UnaryLane(a, out, "abs") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { abs(it) }
}

internal class FloorLane(a: Int, out: Int) : UnaryLane(a, out, "floor") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { floor(it) }
}

internal class FracLane(a: Int, out: Int) : UnaryLane(a, out, "frac") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { it - floor(it) }
}

internal class SinLane(a: Int, out: Int) : UnaryLane(a, out, "sin") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { Trig.sin(it) }
}

internal class CosLane(a: Int, out: Int) : UnaryLane(a, out, "cos") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { Trig.cos(it) }
}

internal class ExpLane(a: Int, out: Int) : UnaryLane(a, out, "exp") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { Exponent.exp(it) }
}

internal class TagLane(a: Int, out: Int, private val vertical: Boolean) : UnaryLane(a, out, "tag$vertical") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val origin = if (vertical) shared.areaY0 else shared.areaX0
        val size = if (vertical) shared.areaHeight else shared.areaWidth
        lanes(registers, count, at) { (it - origin) / size }
    }
}

internal class PixelScreenLane(a: Int, out: Int, private val vertical: Boolean) : UnaryLane(a, out, "screen$vertical") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val origin = if (vertical) shared.areaY0 else shared.areaX0
        val size = if (vertical) shared.areaHeight else shared.areaWidth
        lanes(registers, count, at) { (floor(it) + HALF - origin) / size }
    }

    private companion object {
        const val HALF = 0.5
    }
}

internal class SecondsLane(a: Int, out: Int) : UnaryLane(a, out, "seconds") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val step = shared.step
        val fps = shared.fps
        lanes(registers, count, at) { it * step / fps }
    }
}

internal class AddLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "add") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { x, y -> x + y }
}

internal class SubLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "sub") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { x, y -> x - y }
}

internal class MulLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "mul") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { x, y -> x * y }
}

internal class DivLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "div") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { x, y -> MathNodes.div(x, y) }
}

internal class ModLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "mod") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { x, y -> MathNodes.mod(x, y) }
}

internal class MinLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "min") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { x, y -> min(x, y) }
}

internal class MaxLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "max") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { x, y -> max(x, y) }
}

internal class StepLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "step") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) =
        lanes(registers, count, at) { e, x -> if (x < e) 0.0 else 1.0 }
}

internal class PowLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "pow") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { x, y -> Exponent.pow(x, y) }
}

internal class Atan2Lane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "atan2") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { y, x -> Trig.atan2(y, x) }
}

internal class LightenLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "lighten") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { v, t -> v + (1.0 - v) * t }
}

internal class DarkenLane(a: Int, b: Int, out: Int) : BinaryLane(a, b, out, "darken") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) = lanes(registers, count, at) { v, t -> v * (1.0 - t) }
}

internal class ClampLane(a: Int, b: Int, c: Int, out: Int) : TernaryLane(a, b, c, out, "clamp") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) =
        lanes(registers, count, at) { x, lo, hi -> min(max(x, lo), hi) }
}

internal class MixLane(a: Int, b: Int, c: Int, out: Int) : TernaryLane(a, b, c, out, "mix") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) =
        lanes(registers, count, at) { x, y, t -> MaterialNoise.mix(x, y, t) }
}

internal class SmoothLane(a: Int, b: Int, c: Int, out: Int) : TernaryLane(a, b, c, out, "smooth") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) =
        lanes(registers, count, at) { e0, e1, x -> MathNodes.smoothstep(e0, e1, x) }
}

internal class SelectLane(condition: Int, a: Int, b: Int, out: Int) : TernaryLane(condition, a, b, out, "select") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) =
        lanes(registers, count, at) { c, a, b -> if (c > 0.0) b else a }
}

internal class DotLane(a: IntArray, b: IntArray, out: Int, private val root: Boolean) : CpuOp(a + b, intArrayOf(out)) {
    private val width = a.size

    override val params: String
        get() = "dot$root"

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val at = at
        val o = at[2 * width]
        for (i in 0 until count) {
            var sum = 0.0
            for (c in 0 until width) {
                sum += registers[at[c] + i] * registers[at[width + c] + i]
            }
            registers[o + i] = if (root) sqrt(sum) else sum
        }
    }
}

internal class PolarLane(x: Int, y: Int, out: Int) : CpuOp(intArrayOf(x, y), intArrayOf(out, out + 1)) {
    override val params: String
        get() = "polar"

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val a = at[0]
        val b = at[1]
        val length = at[2]
        val angle = at[3]
        for (i in 0 until count) {
            val x = registers[a + i]
            val y = registers[b + i]
            registers[length + i] = sqrt(x * x + y * y)
            registers[angle + i] = Trig.atan2(y, x)
        }
    }
}

internal class ToneLane(ref: Int, out: Int) : CpuOp(intArrayOf(ref), IntArray(SocketType.VEC4.width) { out + it }) {
    override val params: String
        get() = "tone"

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val palette = shared.palette
        val at = at
        val a = at[0]
        for (i in 0 until count) {
            val ref = registers[a + i].toInt()
            ColorBytes.unpack(if (ref < 0) 0 else palette.color(ref), registers, at, 1, i)
        }
    }
}

internal class SharedLane(private val source: Int, private val index: Int, out: Int, width: Int) :
    CpuOp(IntArray(0), IntArray(width) { out + it }) {
    override val params: String
        get() = "shared$source:$index"

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        for (k in at.indices) {
            val value = when (source) {
                AREA -> if (k == 0) shared.areaWidth else shared.areaHeight
                BODY -> when (k) {
                    0 -> shared.bodyX0
                    1 -> shared.bodyY0
                    2 -> shared.bodyX1
                    else -> shared.bodyY1
                }
                else -> shared.inputs[index]
            }
            registers.fill(value, at[k], at[k] + count)
        }
    }

    companion object {
        const val AREA = 0
        const val BODY = 1
        const val INPUT = 2
    }
}

internal class BodyEdgeLane(x: Int, y: Int, private val base: Int, private val outer: Boolean, out: Int) :
    BinaryLane(x, y, out, "edge$base$outer") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val inputs = shared.inputs
        val grow = if (outer) inputs[base + MaterialInput.BodyEdgeInput.RINGS] else 0.0
        val centerX = (shared.bodyX1 - shared.bodyX0) / 2
        val centerY = (shared.bodyY1 - shared.bodyY0) / 2
        lanes(registers, count, at) { x, y ->
            val cx = x - centerX
            val cy = y - centerY
            val radius = inputs[base + corner(cx, cy)] + grow
            val qx = abs(cx) - centerX - grow + radius
            val qy = abs(cy) - centerY - grow + radius
            val outX = max(qx, 0.0)
            val outY = max(qy, 0.0)
            min(max(qx, qy), 0.0) + sqrt(outX * outX + outY * outY) - radius
        }
    }

    private fun corner(x: Double, y: Double): Int = when {
        x < 0.0 && y < 0.0 -> MaterialInput.BodyEdgeInput.TOP_LEFT
        y < 0.0 -> MaterialInput.BodyEdgeInput.TOP_RIGHT
        x < 0.0 -> MaterialInput.BodyEdgeInput.BOTTOM_LEFT
        else -> MaterialInput.BodyEdgeInput.BOTTOM_RIGHT
    }
}

internal class FirstLane(found: Int, acc: IntArray, value: IntArray, private val slot: Boolean, out: Int) :
    CpuOp(intArrayOf(found) + acc + value, IntArray(acc.size + 1) { out + it }) {
    private val width = acc.size

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val at = at
        val f = at[0]
        val last = at[width * 2]
        val next = at[1 + 2 * width]
        for (i in 0 until count) {
            val was = registers[f + i] != 0.0
            val tail = registers[last + i]
            val take = !was && (if (slot) tail >= 0.0 else tail > 0.0)
            for (c in 0 until width) {
                registers[at[2 + 2 * width + c] + i] = if (take) registers[at[1 + width + c] + i] else registers[at[1 + c] + i]
            }
            registers[next + i] = if (was || take) 1.0 else 0.0
        }
    }
}
