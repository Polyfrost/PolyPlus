package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import kotlin.math.floor

internal object HsvKind : FixedKind(
    "Hsv",
    listOf(
        Socket("h", Accepts.FLOAT),
        Socket("s", Accepts.FLOAT, SocketDefault.Value(1.0)),
        Socket("v", Accepts.FLOAT, SocketDefault.Value(1.0)),
    ),
    SocketType.VEC4,
    unitCost = 12,
) {
    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        compiler.emit(HsvLane(args[0][0], args[1][0], args[2][0], out))
        compiler.constant(out + ColorNodes.RGB, doubleArrayOf(1.0))
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = "nHsv(${args.joinToString(", ")})"
}

internal class RampKind(type: String, sockets: List<Socket>, private val mode: Mode) :
    FixedKind(type, sockets, SocketType.SLOT, unitCost = mode.cost) {
    enum class Mode(val cost: Int) {
        QUANTIZE(3),
        DITHER(8),
        CYCLE(4),
    }

    override fun read(reader: SettingsReader) = reader.slot("slot")

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp {
        val reads = when (mode) {
            Mode.QUANTIZE -> intArrayOf(args[0][0])
            Mode.DITHER -> intArrayOf(args[0][0], args[1][0], args[1][1])
            Mode.CYCLE -> intArrayOf(args[0][0], args[1][0])
        }
        return RampLane(mode, reads, node.settings.slot * Palette.MAX_TONES, node.settings.tones, out)
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String {
        val ramp = "${context.toneBase(node.settings.slot)}, ${node.settings.tones}"
        return when (mode) {
            Mode.QUANTIZE -> "nQuantize(${args[0]}, $ramp)"
            Mode.DITHER -> "nDither(${args[0]}, ${args[1]}, $ramp)"
            Mode.CYCLE -> "nCycle(${args[0]}, ${args[1]}, $ramp)"
        }
    }
}

internal class ShadeKind(type: String, second: String, private val mode: Mode) :
    FixedKind(type, listOf(Socket("color", Accepts.COLOR), Socket(second, Accepts.FLOAT)), SocketType.VEC4, unitCost = 6) {
    enum class Mode {
        LIGHTEN,
        DARKEN,
        ALPHA,
    }

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        val amount = args[1][0]
        for (channel in 0 until ColorNodes.RGB) {
            val value = args[0][channel]
            when (mode) {
                Mode.LIGHTEN -> compiler.emit(LightenLane(value, amount, out + channel))
                Mode.DARKEN -> compiler.emit(DarkenLane(value, amount, out + channel))
                Mode.ALPHA -> compiler.alias(out + channel, value)
            }
        }
        val alpha = args[0][ColorNodes.RGB]
        if (mode == Mode.ALPHA) {
            compiler.emit(MulLane(alpha, amount, out + ColorNodes.RGB))
        } else {
            compiler.alias(out + ColorNodes.RGB, alpha)
        }
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = when (mode) {
        Mode.LIGHTEN -> "nLighten(${args[0]}, ${args[1]})"
        Mode.DARKEN -> "nDarken(${args[0]}, ${args[1]})"
        Mode.ALPHA -> "nAlpha(${args[0]}, ${args[1]})"
    }
}

internal object ColorNodes {
    const val RGB = 3
    const val ROUND = 0.5
    private const val SIXTHS = 6.0
    private const val SECTORS = 6
    private const val THIRD = 3
    private const val FOURTH = 4
    private const val LAST = 5

    fun kinds(): List<NodeKind> = listOf(
        HsvKind,
        RampKind("Quantize", listOf(Socket("level", Accepts.FLOAT)), RampKind.Mode.QUANTIZE),
        RampKind(
            "DitherQuantize",
            listOf(Socket("level", Accepts.FLOAT), Socket("p", Accepts.VEC2, SocketDefault.Input("PixelCell"))),
            RampKind.Mode.DITHER,
        ),
        RampKind(
            "Cycle",
            listOf(Socket("index", Accepts.FLOAT), Socket("shift", Accepts.FLOAT, SocketDefault.Input("Frame"))),
            RampKind.Mode.CYCLE,
        ),
        ShadeKind("Lighten", "amount", ShadeKind.Mode.LIGHTEN),
        ShadeKind("Darken", "amount", ShadeKind.Mode.DARKEN),
        ShadeKind("Alpha", "alpha", ShadeKind.Mode.ALPHA),
    )

    fun level(level: Double, tones: Int, threshold: Double): Int =
        (floor(level * (tones - 1) + threshold)).toInt().coerceIn(0, tones - 1)

    fun hsv(registers: DoubleArray, at: IntArray, lane: Int) {
        val h = registers[at[0] + lane]
        val s = registers[at[1] + lane]
        val v = registers[at[2] + lane]
        val h6 = (h - floor(h)) * SIXTHS
        val sector = floor(h6)
        val f = h6 - sector
        val p = v * (1.0 - s)
        val q = v * (1.0 - s * f)
        val t = v * (1.0 - s * (1.0 - f))
        val index = sector.toInt().mod(SECTORS)
        registers[at[RGB] + lane] = when (index) {
            0, LAST -> v
            1 -> q
            2, THIRD -> p
            else -> t
        }
        registers[at[RGB + 1] + lane] = when (index) {
            0 -> t
            1, 2 -> v
            THIRD -> q
            else -> p
        }
        registers[at[RGB + 2] + lane] = when (index) {
            0, 1 -> p
            2 -> t
            THIRD, FOURTH -> v
            else -> q
        }
    }
}

internal class HsvLane(h: Int, s: Int, v: Int, out: Int) : CpuOp(intArrayOf(h, s, v), IntArray(ColorNodes.RGB) { out + it }) {
    override val params: String
        get() = "hsv"

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val at = at
        for (i in 0 until count) {
            ColorNodes.hsv(registers, at, i)
        }
    }
}

internal class RampLane(private val mode: RampKind.Mode, reads: IntArray, private val base: Int, private val tones: Int, out: Int) :
    CpuOp(reads, intArrayOf(out)) {
    override val params: String
        get() = "ramp$mode:$base:$tones"

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val base = base
        val tones = tones
        when (mode) {
            RampKind.Mode.QUANTIZE ->
                lanes(registers, count, at) { a -> (base + ColorNodes.level(a, tones, ColorNodes.ROUND)).toDouble() }
            RampKind.Mode.DITHER -> {
                val at = at
                val a = at[0]
                val x = at[1]
                val y = at[2]
                val o = at[THIRD]
                for (i in 0 until count) {
                    registers[o + i] = (
                        base + ColorNodes.level(registers[a + i], tones, MaterialNoise.bayer(registers[x + i], registers[y + i]))
                    ).toDouble()
                }
            }
            RampKind.Mode.CYCLE ->
                lanes(registers, count, at) { a, b -> (base + (floor(a).toInt() + floor(b).toInt()).mod(tones)).toDouble() }
        }
    }

    private companion object {
        const val THIRD = 3
    }
}
