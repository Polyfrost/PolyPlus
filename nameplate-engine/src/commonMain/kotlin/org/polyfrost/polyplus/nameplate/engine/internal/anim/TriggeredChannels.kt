package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.Triggers
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangFrame
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track

internal const val UNSET = Int.MIN_VALUE

internal fun phaseOffset(bundle: NameplateBundle, seed: Int): Int = if (bundle.perPlayerPhase) seed.mod(bundle.loop) else 0

internal inline fun triggered(
    triggers: Triggers,
    targetId: String,
    state: TagState,
    time: Int,
    loopTime: Int,
    molang: MolangFrame?,
    select: (ChannelTracks) -> Track?,
): Int {
    if (state.isIdle) return UNSET

    var best = UNSET
    var bestStart = 0
    var bestIndex = 0
    val shots = if (state.isReduced) 0 else state.oneShotCount
    for (i in 0 until shots) {
        val index = state.oneShot(i)
        val shot = triggers.oneShots.getOrNull(index) ?: continue
        val track = shot.channels(targetId)?.let(select) ?: continue
        val start = state.start(i)
        val local = time - start
        val newer = best == UNSET || start > bestStart || (start == bestStart && index < bestIndex)
        if (local in 0 until shot.length && newer) {
            best = track.at(local, molang)
            bestStart = start
            bestIndex = index
        }
    }
    if (best != UNSET || state.inputs.isEmpty) return best

    for (reaction in triggers.whilesOf(targetId)) {
        if (state.inputs.has(reaction.reaction)) {
            val track = reaction.channels(targetId, state.isReduced)?.let(select) ?: continue
            return track.at(loopTime, molang)
        }
    }

    return UNSET
}
