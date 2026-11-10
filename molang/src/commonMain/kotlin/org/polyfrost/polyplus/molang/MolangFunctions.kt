package org.polyfrost.polyplus.molang

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val BASIC = 1
private const val TRIG = 4
private const val POWER = 8
private const val HASH = 2
private const val NOISE = 8
private const val COLOR = 4
private const val DEFAULT_SEED = 0
private const val SEED_ARGS = 3
private const val FBM_SEED_ARGS = 4

internal class MolangFunction(
    val name: String,
    val minArgs: Int,
    val maxArgs: Int,
    val cost: Int,
    val body: Body,
) {
    fun interface Body {
        fun apply(a: Double, b: Double, c: Double, d: Double, count: Int): Double
    }
}

internal object MolangFunctions {
    private val all: Map<String, MolangFunction> = listOf(
        MolangFunction("sin", 1, 1, TRIG) { a, _, _, _, _ -> MolangMath.sinDegrees(a) },
        MolangFunction("cos", 1, 1, TRIG) { a, _, _, _, _ -> MolangMath.cosDegrees(a) },
        MolangFunction("atan2", 2, 2, TRIG) { a, b, _, _, _ -> MolangMath.atan2Degrees(a, b) },
        MolangFunction("abs", 1, 1, BASIC) { a, _, _, _, _ -> abs(a) },
        MolangFunction("min", 2, 2, BASIC) { a, b, _, _, _ -> min(a, b) },
        MolangFunction("max", 2, 2, BASIC) { a, b, _, _, _ -> max(a, b) },
        MolangFunction("clamp", 3, 3, BASIC) { a, b, c, _, _ -> MolangMath.clamp(a, b, c) },
        MolangFunction("lerp", 3, 3, BASIC) { a, b, c, _, _ -> a + (b - a) * c },
        MolangFunction("pow", 2, 2, POWER) { a, b, _, _, _ -> MolangMath.pow(a, b) },
        MolangFunction("sqrt", 1, 1, BASIC) { a, _, _, _, _ -> sqrt(a) },
        MolangFunction("floor", 1, 1, BASIC) { a, _, _, _, _ -> floor(a) },
        MolangFunction("ceil", 1, 1, BASIC) { a, _, _, _, _ -> ceil(a) },
        MolangFunction("round", 1, 1, BASIC) { a, _, _, _, _ -> MolangMath.round(a) },
        MolangFunction("frac", 1, 1, BASIC) { a, _, _, _, _ -> MolangMath.frac(a) },
        MolangFunction("sign", 1, 1, BASIC) { a, _, _, _, _ -> MolangMath.sign(a) },
        MolangFunction("mod", 2, 2, BASIC) { a, b, _, _, _ -> MolangMath.mod(a, b) },
        MolangFunction("floor_mod", 2, 2, BASIC) { a, b, _, _, _ -> MolangMath.floorMod(a, b) },
        MolangFunction("step", 2, 2, BASIC) { a, b, _, _, _ -> MolangMath.step(a, b) },
        MolangFunction("smoothstep", 3, 3, BASIC) { a, b, c, _, _ -> MolangMath.smoothstep(a, b, c) },
        MolangFunction("hash", 3, 3, HASH) { a, b, c, _, _ ->
            MolangMath.hash(MolangMath.cell(a), MolangMath.cell(b), MolangMath.cell(c))
        },
        MolangFunction("noise", 2, SEED_ARGS, NOISE) { a, b, c, _, count ->
            MolangMath.noise(a, b, if (count == SEED_ARGS) MolangMath.cell(c) else DEFAULT_SEED)
        },
        MolangFunction("fbm", SEED_ARGS, FBM_SEED_ARGS, NOISE * MolangLimits.MAX_OCTAVES) { a, b, c, d, count ->
            MolangMath.fbm(a, b, MolangMath.octaves(c), if (count == FBM_SEED_ARGS) MolangMath.cell(d) else DEFAULT_SEED)
        },
        MolangFunction("bayer", 2, 2, HASH) { a, b, _, _, _ -> MolangMath.bayer(MolangMath.cell(a), MolangMath.cell(b)) },
        MolangFunction("hsv", 3, 3, COLOR) { a, b, c, _, _ -> MolangMath.hsv(a, b, c) },
    ).associateBy { it.name }

    fun find(name: String): MolangFunction? = all[name]
}
