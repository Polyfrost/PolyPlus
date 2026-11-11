package org.polyfrost.polyplus.nameplate.engine.api.anim

import org.polyfrost.polyplus.molang.MolangLimits
import org.polyfrost.polyplus.molang.MolangRuntime
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.api.text.LetterFrame
import org.polyfrost.polyplus.nameplate.engine.internal.anim.LetterCache
import org.polyfrost.polyplus.nameplate.engine.internal.anim.latestShot
import org.polyfrost.polyplus.nameplate.engine.internal.anim.phaseOffset
import org.polyfrost.polyplus.nameplate.engine.internal.anim.tagFrame
import org.polyfrost.polyplus.nameplate.engine.internal.query.NameplateQueries
import org.polyfrost.polyplus.nameplate.engine.internal.text.LetterProgram
import org.polyfrost.polyplus.nameplate.engine.internal.text.TextTrack

class TextMotion private constructor(
    private val variants: Map<String, VariantMotion>,
    internal val canMove: Boolean,
    val cost: Int,
) {
    private class VariantMotion(val base: LetterProgram?, val mode: NameplateBundle.TextMode, val canMove: Boolean)

    private val runtimes = HashMap<LetterProgram, MolangRuntime>()
    private val boundVariants = HashMap<MolangRuntime, NameplateBundle.Variant>()
    private val cache = LetterCache()

    var failed: Boolean = false
        private set

    fun modeOf(variant: NameplateBundle.Variant): NameplateBundle.TextMode =
        variants[variant.key]?.mode ?: NameplateBundle.TextMode.VANILLA

    fun evaluate(
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        block: TagBlock,
        frame: Int,
        seed: Int,
        state: TagState,
        count: Int,
        glyphX: IntArray,
        glyphWidth: IntArray,
        into: LetterFrame,
    ): Boolean {
        into.reset(count)
        failed = false
        val motion = variants[variant.key]
        if (motion == null || !motion.canMove || count > LetterFrame.MAX_GLYPHS || state.isReduced) return false

        val phased = tagFrame(bundle, frame, seed)
        val loopTime = phased.mod(bundle.loop)
        val time = phased - phaseOffset(bundle, seed)
        var program = motion.base
        var local = loopTime
        var length = bundle.loop
        val shot = latestShot(bundle.triggers, state, time) { _, candidate -> candidate.text != null }
        if (shot >= 0) {
            val playing = bundle.triggers.oneShots[state.oneShot(shot)]
            program = playing.text?.program
            local = time - state.start(shot)
            length = playing.length
        } else {
            val reacting = activeWhile(bundle, state)
            if (reacting != null) {
                program = reacting.program
            }
        }
        program ?: return false

        val slot = cache.lookup(program, variant, block, local, length, bundle.fps, count, glyphX, glyphWidth)
        if (slot.filled) {
            into.copyFrom(slot.frame)
            failed = slot.failed
            return slot.result
        }
        val result = evaluateProgram(program, bundle, variant, block, local, length, count, glyphX, glyphWidth, into)
        slot.fill(into, failed, result)
        return result
    }

    private fun evaluateProgram(
        program: LetterProgram,
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        block: TagBlock,
        local: Int,
        length: Int,
        count: Int,
        glyphX: IntArray,
        glyphWidth: IntArray,
        into: LetterFrame,
    ): Boolean {
        val runtime = runtimes.getOrPut(program) { program.runtime() }
        runtime.startFrame(MolangLimits.CPU_BUDGET)
        if (boundVariants[runtime] !== variant) {
            NameplateQueries.bindParams(runtime, variant.params)
            boundVariants[runtime] = variant
        }
        NameplateQueries.bindBlock(runtime, block)
        NameplateQueries.bindFrame(runtime, local, length, bundle.fps)
        for (i in 0 until count) {
            NameplateQueries.bindGlyph(runtime, i, count, glyphX[i], glyphWidth[i])
            if (!program.evaluate(runtime, into, i)) {
                into.reset(count)
                failed = true
                return false
            }
        }
        into.finish()
        return into.moving
    }

    private fun activeWhile(bundle: NameplateBundle, state: TagState): TextTrack? {
        if (state.inputs.isEmpty || !bundle.triggers.hasText) return null
        for (reaction in bundle.triggers.whiles) {
            val text = reaction.text ?: continue
            if (state.inputs.has(reaction.reaction)) return text
        }
        return null
    }

    companion object {
        fun of(bundle: NameplateBundle): TextMotion {
            val params = paramNames(bundle)
            val compiled = HashMap<String, LetterProgram?>()
            val variants = HashMap<String, VariantMotion>()
            var cost = 0
            for (variant in bundle.variants.values) {
                val mode = variant.textMode
                val vanilla = mode == NameplateBundle.TextMode.VANILLA
                val source = variant.motion?.takeIf { it.isNotBlank() && !vanilla }
                val base = source?.let { compiled.getOrPut(it) { LetterProgram.compile(it, params, "") {} } }
                variants[variant.key] = VariantMotion(base, mode, !vanilla && (base != null || bundle.triggers.hasText))
                if (!vanilla) {
                    cost = maxOf(cost, worstCost(bundle, base))
                }
            }
            return TextMotion(variants, variants.values.any { it.canMove }, cost)
        }

        private fun paramNames(bundle: NameplateBundle): Set<String> {
            val names = LinkedHashSet(bundle.params.keys)
            for (variant in bundle.variants.values) {
                names.addAll(variant.params.keys)
            }
            return names
        }

        private fun worstCost(bundle: NameplateBundle, base: LetterProgram?): Int {
            var worst = base?.cost ?: 0
            for (shot in bundle.triggers.oneShots) {
                worst = maxOf(worst, shot.text?.program?.cost ?: 0)
            }
            for (reaction in bundle.triggers.whiles) {
                worst = maxOf(worst, reaction.text?.program?.cost ?: 0)
            }
            return worst * LetterFrame.MAX_GLYPHS
        }
    }
}
