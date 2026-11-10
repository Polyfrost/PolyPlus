package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.OneShot
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

    val won = latestShot(triggers, state, time) { _, shot -> shot.channels(targetId)?.let(select) != null }
    if (won >= 0) {
        val track = triggers.oneShots[state.oneShot(won)].channels(targetId)?.let(select) ?: return UNSET
        return track.at(time - state.start(won), molang)
    }
    if (state.inputs.isEmpty) return UNSET

    for (reaction in triggers.whilesOf(targetId)) {
        if (state.inputs.has(reaction.reaction)) {
            val track = reaction.channels(targetId, state.isReduced)?.let(select) ?: continue
            return track.at(loopTime, molang)
        }
    }

    return UNSET
}

internal inline fun latestShot(triggers: Triggers, state: TagState, time: Int, accepts: (Int, OneShot) -> Boolean): Int {
    var won = -1
    var wonStart = 0
    var wonIndex = 0
    val shots = if (state.isReduced) 0 else state.oneShotCount
    for (i in 0 until shots) {
        val index = state.oneShot(i)
        val shot = triggers.oneShots.getOrNull(index) ?: continue
        if (!accepts(index, shot)) continue
        val start = state.start(i)
        val newer = won < 0 || start > wonStart || (start == wonStart && index < wonIndex)
        if (time - start in 0 until shot.length && newer) {
            won = i
            wonStart = start
            wonIndex = index
        }
    }
    return won
}
