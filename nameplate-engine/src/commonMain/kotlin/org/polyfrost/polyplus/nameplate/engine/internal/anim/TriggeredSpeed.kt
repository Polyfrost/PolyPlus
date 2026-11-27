package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState

internal object TriggeredSpeed {
    private const val MAX_STEPS = 600

    fun at(bundle: NameplateBundle, layerId: String, state: TagState, step: Int, layerStep: Int, phase: Int): Int {
        if (state.isIdle) return UNSET

        val triggers = bundle.triggers
        val frame = step * layerStep
        val raw = frame - phase
        val won = latestShot(triggers, state, raw) { _, shot -> layerId in shot.speeds }
        if (won >= 0) {
            val track = triggers.oneShots[state.oneShot(won)].speeds.getValue(layerId)
            return track.at(raw - state.start(won))
        }
        if (state.inputs.isEmpty) return UNSET

        for (reaction in triggers.whiles) {
            if (!state.inputs.has(reaction.reaction) || (state.isReduced && reaction.dropsWhenReduced)) continue
            val track = reaction.speeds[layerId] ?: continue
            return track.at(if (state.isReduced) 0 else frame.mod(bundle.loop))
        }
        return UNSET
    }

    fun integrate(bundle: NameplateBundle, state: TagState, shifts: LongArray, from: Int, to: Int, phase: Int): Boolean {
        var changed = false
        for ((i, layerId) in bundle.triggers.speedTargets.withIndex()) {
            val layer = bundle.layers.firstOrNull { it.id == layerId } ?: continue
            val path = (layer.content as? LayerContent.Sprite)?.adaptive?.path ?: continue
            val end = (to + phase).floorDiv(layer.step)
            var step = maxOf((from + phase).floorDiv(layer.step), end - MAX_STEPS)
            var delta = 0L
            while (step < end) {
                val speed = at(bundle, layerId, state, step, layer.step, phase)
                if (speed != UNSET) {
                    delta += speed - path.speed.at(step.mod(path.cycle))
                }
                step++
            }
            if (delta != 0L) {
                shifts[i] += delta
                changed = true
            }
        }
        return changed
    }
}
