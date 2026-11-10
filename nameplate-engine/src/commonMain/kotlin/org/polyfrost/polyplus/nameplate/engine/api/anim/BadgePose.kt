package org.polyfrost.polyplus.nameplate.engine.api.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.phaseOffset
import org.polyfrost.polyplus.nameplate.engine.internal.anim.sample
import org.polyfrost.polyplus.nameplate.engine.internal.anim.tagFrame
import org.polyfrost.polyplus.nameplate.engine.internal.anim.triggered
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.BADGE_TARGET

fun badgePose(bundle: NameplateBundle, frame: Int, seed: Int, state: TagState = TagState.NONE): LayerPose {
    val loop = bundle.loopChannels[BADGE_TARGET]
    if (loop == null && BADGE_TARGET !in bundle.triggers.targets) return LayerPose.IDENTITY

    val phased = tagFrame(bundle, frame, seed)
    val loopTime = phased.mod(bundle.loop)
    val time = phased - phaseOffset(bundle, seed)
    val triggers = bundle.triggers
    val movedX = triggered(triggers, BADGE_TARGET, state, time, loopTime, null) { it.positionX }
    val movedY = triggered(triggers, BADGE_TARGET, state, time, loopTime, null) { it.positionY }
    val reacted = triggered(triggers, BADGE_TARGET, state, time, loopTime, null) { it.opacity }
    val shown = triggered(triggers, BADGE_TARGET, state, time, loopTime, null) { it.visible }
    return LayerPose(
        sample(null, loop, 0, loopTime, null, 0, movedX) { it.positionX },
        sample(null, loop, 0, loopTime, null, 0, movedY) { it.positionY },
        sample(null, loop, 0, loopTime, null, LayerPose.OPAQUE, reacted) { it.opacity },
        sample(null, loop, 0, loopTime, null, 1, shown) { it.visible } != 0,
    )
}
