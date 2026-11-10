package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.anim.TagRecord
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagInputs
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.mirrorTail
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Trigger

internal class ReactionPlan(private val toggled: Int, private val repeatShots: Boolean) {
    fun start(bundle: NameplateBundle): ReactionSequence? =
        if (toggled == 0 && !repeatShots) null else ReactionSequence(bundle, toggled, repeatShots)

    companion object {
        val NONE: ReactionPlan = ReactionPlan(0, false)

        private const val HURT_TICKS = 10
        private const val TICKS_PER_SECOND = 20

        fun hurtPeriod(fps: Int): Int = maxOf(2, (fps * HURT_TICKS + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND)

        fun repeats(bundle: NameplateBundle): Boolean =
            bundle.triggers.oneShots.any { it.trigger is Trigger.Appear || it.trigger == Trigger.Equip }

        fun span(bundle: NameplateBundle): Int {
            val longest = bundle.triggers.oneShots.maxOfOrNull { it.length } ?: 0
            return longest + mirrorTail(bundle) + hurtPeriod(bundle.fps)
        }

        fun maskOf(reactions: Collection<Reaction>): Int = reactions.fold(0) { mask, reaction -> mask or reaction.bit }
    }
}

internal class ReactionSequence(
    private val bundle: NameplateBundle,
    private val toggled: Int,
    private val repeatShots: Boolean,
) {
    private var now = 0L
    private val record = TagRecord<Int>(1) { now }
    private val variant = bundle.variants.values.first()
    private val hurtPeriod = ReactionPlan.hurtPeriod(bundle.fps)
    private val repeated = bundle.triggers.oneShots.indices
        .filter { bundle.triggers.oneShots[it].trigger.let { trigger -> trigger is Trigger.Appear || trigger == Trigger.Equip } }
        .toIntArray()

    fun at(frame: Int): TagState {
        now = frame * MILLIS_PER_SECOND / bundle.fps
        val state = record.update(KEY, bundle, variant, frame, inputs(frame))
        if (!repeatShots) return state

        var fired = false
        for (index in repeated) {
            fired = record.fireIgnoringRestart(KEY, bundle, index, frame) || fired
        }
        return if (fired) record.state(KEY) else state
    }

    private fun inputs(frame: Int): TagInputs {
        val hurt = Reaction.HURT.bit
        var mask = if (frame % 2 == 0) toggled and hurt.inv() else 0
        if (toggled and hurt != 0 && frame % hurtPeriod != hurtPeriod - 1) {
            mask = mask or hurt
        }

        return TagInputs.ofMask(mask)
    }

    private companion object {
        const val KEY = 0
        const val MILLIS_PER_SECOND = 1_000L
    }
}
