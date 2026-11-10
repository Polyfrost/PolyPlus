package org.polyfrost.polyplus.nameplate.engine.api.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.channelAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.phaseOffset
import org.polyfrost.polyplus.nameplate.engine.internal.anim.tagFrame
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.BADGE_TARGET

fun badgePose(bundle: NameplateBundle, frame: Int, seed: Int, state: TagState = TagState.NONE): LayerPose {
    val loop = bundle.loopChannels[BADGE_TARGET]
    if (loop == null && BADGE_TARGET !in bundle.triggers.targets) return LayerPose.IDENTITY

    val phased = tagFrame(bundle, frame, seed)
    val loopTime = phased.mod(bundle.loop)
    val time = phased - phaseOffset(bundle, seed)
    val triggers = bundle.triggers
    return LayerPose(
        channelAt(triggers, BADGE_TARGET, null, loop, state, time, loopTime, 0, null, 0) { it.positionX },
        channelAt(triggers, BADGE_TARGET, null, loop, state, time, loopTime, 0, null, 0) { it.positionY },
        channelAt(triggers, BADGE_TARGET, null, loop, state, time, loopTime, 0, null, LayerPose.OPAQUE) { it.opacity },
        channelAt(triggers, BADGE_TARGET, null, loop, state, time, loopTime, 0, null, 1) { it.visible } != 0,
    )
}
