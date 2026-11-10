package org.polyfrost.polyplus.nameplate.engine.internal.material

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class UnaryKind(
    type: String,
    private val lane: (Int, Int) -> CpuOp,
    private val code: String,
    private val unitCost: Int,
) : NodeKind(type, listOf(Socket("x", Accepts.NUMBER))) {
    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typings.elementwise(sockets, inputs)

    override fun own(node: GraphNode): Int = unitCost * node.type.width

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        for (k in 0 until node.type.width) {
            compiler.emit(lane(args[0][k], out + k))
        }
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = "$code(${args[0]})"
}

internal class BinaryKind(
    type: String,
    names: List<String>,
    private val lane: (Int, Int, Int) -> CpuOp,
    private val code: (String, String) -> String,
    private val unitCost: Int,
) : NodeKind(type, names.map { Socket(it, Accepts.NUMBER) }) {
    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typings.elementwise(sockets, inputs)

    override fun own(node: GraphNode): Int = unitCost * node.type.width

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        for (k in 0 until node.type.width) {
            compiler.emit(lane(args[0][k], args[1][k], out + k))
        }
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = code(args[0], args[1])
}

internal class TernaryKind(
    type: String,
    names: List<String>,
    private val lane: (Int, Int, Int, Int) -> CpuOp,
    private val code: String,
    private val unitCost: Int,
) : NodeKind(type, names.map { Socket(it, Accepts.NUMBER) }) {
    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typings.elementwise(sockets, inputs)

    override fun own(node: GraphNode): Int = unitCost * node.type.width

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        for (k in 0 until node.type.width) {
            compiler.emit(lane(args[0][k], args[1][k], args[2][k], out + k))
        }
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = "$code(${args.joinToString(", ")})"
}

internal class DotKind(type: String, private val length: Boolean) :
    NodeKind(type, (if (length) listOf("v") else listOf("a", "b")).map { Socket(it, Accepts.NUMBER) }) {
    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing =
        when (val typing = Typings.elementwise(sockets, inputs)) {
            is Typing.Ok -> Typing.Ok(SocketType.FLOAT, typing.targets)
            is Typing.Error -> typing
        }

    override fun own(node: GraphNode): Int =
        1 + 2 * (node.inputs.firstOrNull()?.target?.width ?: 1) + if (length) ROOT_COST else 0

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp {
        val width = node.inputs[0].target?.width ?: 1
        return DotLane(args[0].take(width), (if (length) args[0] else args[1]).take(width), out, length)
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String =
        if (length) "length(${args[0]})" else "dot(${args[0]}, ${args[1]})"

    private companion object {
        const val ROOT_COST = 4
    }
}

internal object PolarKind : FixedKind("polar", listOf(Socket("v", Accepts.VEC2)), SocketType.VEC2, unitCost = 44) {
    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp =
        PolarLane(args[0][0], args[0][1], out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = "nPolar(${args[0]})"
}

internal object MathNodes {
    private const val TRIG = 9
    private const val EXP = 14
    private const val ATAN = 40
    private const val POW = 70
    private const val DIV = 2
    private const val MOD = 3
    private const val SMOOTH = 5
    private const val SMOOTH_BASE = 3.0

    private val PAIR = listOf("a", "b")

    fun kinds(): List<NodeKind> = listOf(
        UnaryKind("abs", ::AbsLane, "abs", 1),
        UnaryKind("floor", ::FloorLane, "floor", 1),
        UnaryKind("frac", ::FracLane, "fract", 1),
        UnaryKind("sin", ::SinLane, "sin", TRIG),
        UnaryKind("cos", ::CosLane, "cos", TRIG),
        UnaryKind("exp", ::ExpLane, "exp", EXP),
        BinaryKind("add", PAIR, ::AddLane, { a, b -> "($a + $b)" }, 1),
        BinaryKind("sub", PAIR, ::SubLane, { a, b -> "($a - $b)" }, 1),
        BinaryKind("mul", PAIR, ::MulLane, { a, b -> "($a * $b)" }, 1),
        BinaryKind("div", PAIR, ::DivLane, { a, b -> "nDiv($a, $b)" }, DIV),
        BinaryKind("mod", PAIR, ::ModLane, { a, b -> "nMod($a, $b)" }, MOD),
        BinaryKind("min", PAIR, ::MinLane, { a, b -> "min($a, $b)" }, 1),
        BinaryKind("max", PAIR, ::MaxLane, { a, b -> "max($a, $b)" }, 1),
        BinaryKind("step", listOf("edge", "x"), ::StepLane, { e, x -> "step($e, $x)" }, 1),
        BinaryKind("pow", listOf("x", "y"), ::PowLane, { a, b -> "nPow($a, $b)" }, POW),
        BinaryKind("atan2", listOf("y", "x"), ::Atan2Lane, { y, x -> "nAtan2($y, $x)" }, ATAN),
        TernaryKind("clamp", listOf("x", "min", "max"), ::ClampLane, "nClamp", 2),
        TernaryKind("mix", listOf("a", "b", "t"), ::MixLane, "nMix", 2),
        TernaryKind("smoothstep", listOf("edge0", "edge1", "x"), ::SmoothLane, "nSmooth", SMOOTH),
        DotKind("length", length = true),
        DotKind("dot", length = false),
        PolarKind,
    )

    fun div(a: Double, b: Double): Double = if (b == 0.0) 0.0 else a / b

    fun mod(a: Double, b: Double): Double = if (b == 0.0) 0.0 else a - b * floor(a / b)

    fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = min(max(div(x - edge0, edge1 - edge0), 0.0), 1.0)
        return t * t * (SMOOTH_BASE - 2.0 * t)
    }
}
