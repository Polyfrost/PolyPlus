package org.polyfrost.polyplus.nameplate.engine.internal.text

import org.polyfrost.polyplus.molang.MolangCompiler
import org.polyfrost.polyplus.molang.MolangEnvironment
import org.polyfrost.polyplus.molang.MolangIssue
import org.polyfrost.polyplus.molang.MolangProgram
import org.polyfrost.polyplus.molang.MolangRuntime
import org.polyfrost.polyplus.molang.MolangValue
import org.polyfrost.polyplus.nameplate.engine.api.text.LetterFrame
import org.polyfrost.polyplus.nameplate.engine.internal.query.MolangUse
import org.polyfrost.polyplus.nameplate.engine.internal.query.NameplateQueries
import org.polyfrost.polyplus.nameplate.engine.internal.query.NameplateQuery

internal class LetterProgram(val source: String, private val program: MolangProgram) {
    private val environment: MolangEnvironment = program.environment
    private val xSlot = environment.variableSlot(X)
    private val opacitySlot = environment.variableSlot(OPACITY)
    private val colorSlot = environment.variableSlot(COLOR)
    private val shadowSlot = environment.variableSlot(SHADOW)
    private val outlineSlot = environment.variableSlot(OUTLINE)
    private val scaleSlot = environment.variableSlot(SCALE)
    private val rotationSlot = environment.variableSlot(ROTATION)
    private val names: Set<String> = names(source)

    val readsBlock: Boolean = BLOCK_QUERIES.any { it in names }
    val readsGlyphX: Boolean = NameplateQuery.GLYPH_X.id in names
    val readsGlyphWidth: Boolean = NameplateQuery.GLYPH_WIDTH.id in names
    val readsParams: Boolean = VARIANT_PARAM in names

    val cost: Int
        get() = program.cost

    val writesOpacity: Boolean
        get() = opacitySlot != MolangEnvironment.NONE

    val writesColor: Boolean
        get() = colorSlot != MolangEnvironment.NONE

    val ownedOutputs: List<String>
        get() = OWNED_ONLY.filter { environment.variableSlot(it) != MolangEnvironment.NONE }

    fun runtime(): MolangRuntime = MolangRuntime(environment)

    fun evaluate(runtime: MolangRuntime, into: LetterFrame, glyph: Int): Boolean {
        runtime.clearVariables()
        preset(runtime, opacitySlot, 1.0)
        preset(runtime, colorSlot, KEEP_VALUE)
        preset(runtime, shadowSlot, KEEP_VALUE)
        preset(runtime, outlineSlot, KEEP_VALUE)
        preset(runtime, scaleSlot, 1.0)
        val dy = program.evaluate(runtime)
        if (runtime.failed) return false
        into.dy[glyph] = offset(dy)
        into.dx[glyph] = offset(read(runtime, xSlot, 0.0))
        into.opacity[glyph] = MolangValue.snap(read(runtime, opacitySlot, 1.0) * LetterFrame.OPAQUE).coerceIn(0, LetterFrame.OPAQUE)
        into.color[glyph] = color(read(runtime, colorSlot, KEEP_VALUE))
        into.shadow[glyph] = decoration(read(runtime, shadowSlot, KEEP_VALUE))
        into.outline[glyph] = decoration(read(runtime, outlineSlot, KEEP_VALUE))
        into.scale[glyph] = MolangValue.snap(read(runtime, scaleSlot, 1.0) * LetterFrame.SCALE_UNITS)
            .coerceIn(LetterFrame.MIN_SCALE, LetterFrame.MAX_SCALE)
        into.rotation[glyph] = MolangValue.snap(read(runtime, rotationSlot, 0.0)).mod(FULL_TURN)
        return true
    }

    private fun preset(runtime: MolangRuntime, slot: Int, value: Double) {
        if (slot != MolangEnvironment.NONE) {
            runtime.setVariable(slot, value)
        }
    }

    private fun read(runtime: MolangRuntime, slot: Int, fallback: Double): Double =
        if (slot == MolangEnvironment.NONE) fallback else runtime.variable(slot)

    companion object {
        const val X = "v.x"
        const val OPACITY = "v.opacity"
        const val COLOR = "v.color"
        const val SHADOW = "v.shadow"
        const val OUTLINE = "v.outline"
        const val SCALE = "v.scale"
        const val ROTATION = "v.rotation"

        val OWNED_ONLY: List<String> = listOf(COLOR, SHADOW, OUTLINE, SCALE, ROTATION)

        const val MAX_OFFSET = 32

        private const val KEEP_VALUE = -1.0
        private const val FULL_TURN = 360
        private const val RGB_MAX = 0xFFFFFF
        private const val VARIANT_PARAM = "variant_param"
        private val NON_NAME = Regex("[^a-z0-9_]+")

        private val BLOCK_QUERIES: List<String> = listOf(
            NameplateQuery.WIDTH,
            NameplateQuery.HEIGHT,
            NameplateQuery.LINE_COUNT,
            NameplateQuery.NAME_X,
            NameplateQuery.NAME_Y,
            NameplateQuery.NAME_WIDTH,
            NameplateQuery.PREFIX_WIDTH,
            NameplateQuery.BG_X0,
            NameplateQuery.BG_Y0,
            NameplateQuery.BG_X1,
            NameplateQuery.BG_Y1,
        ).map { it.id }

        fun environment(params: Collection<String>): MolangEnvironment = NameplateQueries.environment(MolangUse.TEXT, params)

        fun compile(source: String, params: Collection<String>, path: String, report: (MolangIssue) -> Unit): LetterProgram? {
            val result = MolangCompiler.compile(source, environment(params), path)
            result.issues.forEach(report)
            return result.program?.let { LetterProgram(source, it) }
        }

        private fun names(source: String): Set<String> {
            val names = HashSet<String>()
            for (token in source.lowercase().split(NON_NAME)) {
                val name = token.trimStart { it in '0'..'9' }
                if (name.isNotEmpty()) {
                    names.add(name)
                }
            }
            return names
        }

        private fun offset(value: Double): Int = MolangValue.snap(value).coerceIn(-MAX_OFFSET, MAX_OFFSET)

        private fun color(value: Double): Int = when {
            !value.isFinite() || value < 0.0 -> LetterFrame.KEEP
            else -> MolangValue.argb(value) and RGB_MAX
        }

        private fun decoration(value: Double): Int = when {
            !value.isFinite() || value == KEEP_VALUE -> LetterFrame.KEEP
            value < 0.0 -> LetterFrame.OFF
            else -> MolangValue.argb(value) and RGB_MAX
        }
    }
}
