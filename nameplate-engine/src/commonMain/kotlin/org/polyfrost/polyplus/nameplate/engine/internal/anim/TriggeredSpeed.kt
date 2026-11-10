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
        var best = UNSET
        var bestStart = 0
        var bestIndex = 0
        if (!state.isReduced) {
            for (i in 0 until state.oneShotCount) {
                val index = state.oneShot(i)
                val shot = triggers.oneShots.getOrNull(index) ?: continue
                val track = shot.speeds[layerId] ?: continue
                val start = state.start(i)
                val local = raw - start
                val newer = best == UNSET || start > bestStart || (start == bestStart && index < bestIndex)
                if (local in 0 until shot.length && newer) {
                    best = track.at(local)
                    bestStart = start
                    bestIndex = index
                }
            }
        }
        if (best != UNSET || state.inputs.isEmpty) return best

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
