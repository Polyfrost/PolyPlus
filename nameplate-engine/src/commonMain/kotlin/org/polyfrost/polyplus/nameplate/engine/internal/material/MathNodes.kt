package org.polyfrost.polyplus.nameplate.engine.internal.material

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class ElementwiseKind(
    type: String,
    names: List<String>,
    private val lane: LaneFn,
    private val unitCost: Int,
    private val code: (List<String>) -> String,
) : NodeKind(type, names.map { Socket(it, Accepts.NUMBER) }) {
    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typings.elementwise(sockets, inputs)

    override fun own(node: GraphNode): Int = unitCost * node.type.width

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        for (k in 0 until node.type.width) {
            compiler.emit(MathLane(lane, out + k, *IntArray(args.size) { args[it][k] }))
        }
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = code(args)
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

    private val UNARY = listOf("x")
    private val PAIR = listOf("a", "b")

    private fun call(name: String): (List<String>) -> String = { args -> "$name(${args.joinToString(", ")})" }

    private fun infix(operator: String): (List<String>) -> String = { (a, b) -> "($a $operator $b)" }

    fun kinds(): List<NodeKind> = listOf(
        ElementwiseKind("abs", UNARY, LaneFn.ABS, 1, call("abs")),
        ElementwiseKind("floor", UNARY, LaneFn.FLOOR, 1, call("floor")),
        ElementwiseKind("frac", UNARY, LaneFn.FRAC, 1, call("fract")),
        ElementwiseKind("sin", UNARY, LaneFn.SIN, TRIG, call("sin")),
        ElementwiseKind("cos", UNARY, LaneFn.COS, TRIG, call("cos")),
        ElementwiseKind("exp", UNARY, LaneFn.EXP, EXP, call("exp")),
        ElementwiseKind("add", PAIR, LaneFn.ADD, 1, infix("+")),
        ElementwiseKind("sub", PAIR, LaneFn.SUB, 1, infix("-")),
        ElementwiseKind("mul", PAIR, LaneFn.MUL, 1, infix("*")),
        ElementwiseKind("div", PAIR, LaneFn.DIV, DIV, call("nDiv")),
        ElementwiseKind("mod", PAIR, LaneFn.MOD, MOD, call("nMod")),
        ElementwiseKind("min", PAIR, LaneFn.MIN, 1, call("min")),
        ElementwiseKind("max", PAIR, LaneFn.MAX, 1, call("max")),
        ElementwiseKind("step", listOf("edge", "x"), LaneFn.STEP, 1, call("step")),
        ElementwiseKind("pow", listOf("x", "y"), LaneFn.POW, POW, call("nPow")),
        ElementwiseKind("atan2", listOf("y", "x"), LaneFn.ATAN2, ATAN, call("nAtan2")),
        ElementwiseKind("clamp", listOf("x", "min", "max"), LaneFn.CLAMP, 2, call("nClamp")),
        ElementwiseKind("mix", listOf("a", "b", "t"), LaneFn.MIX, 2, call("nMix")),
        ElementwiseKind("smoothstep", listOf("edge0", "edge1", "x"), LaneFn.SMOOTH, SMOOTH, call("nSmooth")),
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
