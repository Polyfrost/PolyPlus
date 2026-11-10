package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class NoiseKind(type: String, private val fractal: Boolean) :
    FixedKind(type, listOf(Socket("p", Accepts.VEC2)), SocketType.FLOAT) {
    override fun read(reader: SettingsReader) {
        val settings = reader.settings
        settings.seed = reader.int("seed", 0..Int.MAX_VALUE, 0)
        val period = reader.pair("period", 0..MaterialLimits.MAX_PERIOD, intArrayOf(0, 0))
        settings.periodX = period[0]
        settings.periodY = period[1]
        if (fractal) {
            settings.octaves = reader.int("octaves", 1..MaterialLimits.MAX_OCTAVES, DEFAULT_OCTAVES)
        }
    }

    override fun own(node: GraphNode): Int = NOISE_COST * if (fractal) node.settings.octaves else 1

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp =
        NoiseLane(args[0][0], args[0][1], node.settings, fractal, out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String {
        val s = node.settings
        val period = "ivec2(${s.periodX}, ${s.periodY})"
        return if (fractal) "nFbm(${args[0]}, ${s.octaves}, $period, ${s.seed})" else "nNoise(${args[0]}, $period, ${s.seed})"
    }

    private companion object {
        const val NOISE_COST = 52
        const val DEFAULT_OCTAVES = 3
    }
}

internal class NoiseLane(x: Int, y: Int, private val settings: NodeSettings, private val fractal: Boolean, out: Int) :
    CpuOp(intArrayOf(x, y), intArrayOf(out)) {
    override val params: String
        get() = "noise$fractal:${settings.seed}:${settings.periodX}:${settings.periodY}:${settings.octaves}"

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val settings = settings
        if (fractal) {
            lanes(registers, count, at) { x, y -> MaterialNoise.fbm(x, y, settings) }
        } else {
            val periodX = settings.periodX
            val periodY = settings.periodY
            val seed = settings.seed
            lanes(registers, count, at) { x, y -> MaterialNoise.noise(x, y, periodX, periodY, seed) }
        }
    }
}

internal class HashLane(x: Int, y: Int, private val seed: Int, out: Int) : BinaryLane(x, y, out, "hash$seed") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val seed = seed
        lanes(registers, count, at) { x, y -> MaterialNoise.hash(floor(x).toInt(), floor(y).toInt(), seed) }
    }
}

internal class BayerLane(x: Int, y: Int, out: Int) : BinaryLane(x, y, out, "bayer") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) =
        lanes(registers, count, at) { x, y -> MaterialNoise.bayer(x, y) }
}

internal class CheckerLane(x: Int, y: Int, size: Int, out: Int) : BinaryLane(x, y, out, "checker$size") {
    private val size = size.toDouble()

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val size = size
        lanes(registers, count, at) { x, y -> ((floor(x / size) + floor(y / size)).toInt().mod(2)).toDouble() }
    }
}

internal class StripesLane(x: Int, y: Int, private val settings: NodeSettings, out: Int) :
    BinaryLane(x, y, out, "stripes${settings.size}:${settings.width}:${settings.directionX}:${settings.directionY}") {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val dx = settings.directionX
        val dy = settings.directionY
        val size = settings.size
        val width = settings.width
        lanes(registers, count, at) { x, y -> if ((floor(x).toInt() * dx + floor(y).toInt() * dy).mod(size) < width) 1.0 else 0.0 }
    }
}

internal class GradientLane(reads: IntArray, out: Int) : CpuOp(reads, intArrayOf(out)) {
    override val params: String
        get() = "gradient"

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val at = at
        val px = at[0]
        val py = at[1]
        val ax = at[2]
        val ay = at[P_AY]
        val bx = at[P_BX]
        val by = at[P_BY]
        val o = at[P_OUT]
        for (i in 0 until count) {
            val dx = registers[bx + i] - registers[ax + i]
            val dy = registers[by + i] - registers[ay + i]
            val along = (registers[px + i] - registers[ax + i]) * dx + (registers[py + i] - registers[ay + i]) * dy
            registers[o + i] = min(max(MathNodes.div(along, dx * dx + dy * dy), 0.0), 1.0)
        }
    }

    private companion object {
        const val P_AY = 3
        const val P_BX = 4
        const val P_BY = 5
        const val P_OUT = 6
    }
}

internal object HashKind : FixedKind("Hash", listOf(Socket("p", Accepts.VEC2)), SocketType.FLOAT, unitCost = 10) {
    override fun read(reader: SettingsReader) {
        reader.settings.seed = reader.int("seed", 0..Int.MAX_VALUE, 0)
    }

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp =
        HashLane(args[0][0], args[0][1], node.settings.seed, out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = "nHashAt(${args[0]}, ${node.settings.seed})"
}

internal object BayerKind : FixedKind(
    "Bayer",
    listOf(Socket("p", Accepts.VEC2, SocketDefault.Input("PixelCell"))),
    SocketType.FLOAT,
    unitCost = 6,
) {
    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp = BayerLane(args[0][0], args[0][1], out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = "nBayer(${args[0]})"
}

internal object CheckerKind : FixedKind(
    "Checker",
    listOf(Socket("p", Accepts.VEC2, SocketDefault.Input("PixelCell"))),
    SocketType.FLOAT,
    unitCost = 5,
) {
    override fun read(reader: SettingsReader) {
        reader.settings.size = reader.int("size", 1..MaterialLimits.MAX_CELL_SIZE, 1)
    }

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp =
        CheckerLane(args[0][0], args[0][1], node.settings.size, out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = "nChecker(${args[0]}, ${node.settings.size})"
}

internal object StripesKind : FixedKind(
    "Stripes",
    listOf(Socket("p", Accepts.VEC2, SocketDefault.Input("PixelCell"))),
    SocketType.FLOAT,
    unitCost = 5,
) {
    private const val MAX_DIRECTION = 16

    override fun read(reader: SettingsReader) {
        val settings = reader.settings
        settings.size = reader.int("period", 1..MaterialLimits.MAX_CELL_SIZE, 2)
        settings.width = reader.int("width", 1..MaterialLimits.MAX_CELL_SIZE, 1)
        val direction = reader.pair("direction", -MAX_DIRECTION..MAX_DIRECTION, intArrayOf(1, 0))
        settings.directionX = direction[0]
        settings.directionY = direction[1]
    }

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp =
        StripesLane(args[0][0], args[0][1], node.settings, out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String {
        val s = node.settings
        return "nStripes(${args[0]}, ${s.size}, ${s.width}, ivec2(${s.directionX}, ${s.directionY}))"
    }
}

internal object GradientKind : FixedKind(
    "Gradient",
    listOf(
        Socket("p", Accepts.VEC2, SocketDefault.Input("TagUV")),
        Socket("from", Accepts.VEC2, SocketDefault.Value(0.0, 0.0)),
        Socket("to", Accepts.VEC2, SocketDefault.Value(1.0, 0.0)),
    ),
    SocketType.FLOAT,
    unitCost = 8,
) {
    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp =
        GradientLane(intArrayOf(args[0][0], args[0][1], args[1][0], args[1][1], args[2][0], args[2][1]), out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String =
        "nGradient(${args[0]}, ${args[1]}, ${args[2]})"
}
