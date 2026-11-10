package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.Triggers
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangFrame
import org.polyfrost.polyplus.nameplate.engine.internal.track.PoseTarget
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track

internal const val SALT_MIRROR = 4

private const val OPACITY_HALF = 127

internal fun tagFrame(bundle: NameplateBundle, frame: Int, seed: Int): Int =
    if (bundle.perPlayerPhase) frame + seed.mod(bundle.loop) else frame

internal fun poseAt(
    bundle: NameplateBundle,
    layer: NameplateBundle.Layer,
    frame: Int,
    seed: Int,
    mirrored: Boolean = false,
    state: TagState = TagState.NONE,
    molang: MolangFrame? = null,
): LayerPose {
    val chain = bundle.poseChain(layer.id)
    if (chain.isEmpty()) return LayerPose.IDENTITY

    val shifted = mirrorFrame(layer, frame, mirrored)
    val rolled = mirrorSeed(layer, seed, mirrored)
    val loopTime = shifted.mod(bundle.loop)
    val time = shifted - phaseOffset(bundle, seed)
    val triggers = bundle.triggers
    var dx = 0
    var dy = 0
    var opacity = LayerPose.OPAQUE
    var visible = true
    for (target in chain) {
        val slot = if (target.schedule == null) NO_EVENT else scheduledSlot(target.schedule, shifted, rolled, target.step)
        val event = scheduledChannels(target, slot)
        val local = localOf(slot)
        val id = target.id
        dx += channelAt(triggers, id, event, target.loop, state, time, loopTime, local, molang, 0) { it.positionX }
        dy += channelAt(triggers, id, event, target.loop, state, time, loopTime, local, molang, 0) { it.positionY }
        val opaque = channelAt(triggers, id, event, target.loop, state, time, loopTime, local, molang, LayerPose.OPAQUE) { it.opacity }
        opacity = multiply(opacity, opaque)
        visible = visible && channelAt(triggers, id, event, target.loop, state, time, loopTime, local, molang, 1) { it.visible } != 0
    }
    return LayerPose(dx, dy, opacity, visible)
}

internal fun followAt(
    bundle: NameplateBundle,
    layer: NameplateBundle.Layer,
    frame: Int,
    seed: Int,
    state: TagState = TagState.NONE,
    molang: MolangFrame? = null,
): LayerPose = positionAt(bundle, bundle.followChain(layer.id), frame, seed, state, molang)

internal fun plateAt(
    bundle: NameplateBundle,
    frame: Int,
    seed: Int,
    state: TagState = TagState.NONE,
    molang: MolangFrame? = null,
): LayerPose = positionAt(bundle, bundle.plateChain, frame, seed, state, molang)

internal fun badgeFollowAt(
    bundle: NameplateBundle,
    variant: NameplateBundle.Variant,
    frame: Int,
    seed: Int,
    state: TagState = TagState.NONE,
    molang: MolangFrame? = null,
): LayerPose {
    val anchor = variant.badge.placement.anchor as? EdgeBadgeAnchor ?: return LayerPose.IDENTITY
    return positionAt(bundle, bundle.poseChain(anchor.of), frame, seed, state, molang)
}

private fun positionAt(
    bundle: NameplateBundle,
    chain: Array<PoseTarget>,
    frame: Int,
    seed: Int,
    state: TagState,
    molang: MolangFrame?,
): LayerPose {
    if (chain.isEmpty()) return LayerPose.IDENTITY

    val loopTime = frame.mod(bundle.loop)
    val time = frame - phaseOffset(bundle, seed)
    val triggers = bundle.triggers
    var dx = 0
    var dy = 0
    for (target in chain) {
        val slot = if (target.schedule == null) NO_EVENT else scheduledSlot(target.schedule, frame, seed, target.step)
        val event = scheduledChannels(target, slot)
        val local = localOf(slot)
        val id = target.id
        dx += channelAt(triggers, id, event, target.loop, state, time, loopTime, local, molang, 0) { it.positionX }
        dy += channelAt(triggers, id, event, target.loop, state, time, loopTime, local, molang, 0) { it.positionY }
    }
    return if (dx == 0 && dy == 0) LayerPose.IDENTITY else LayerPose(dx, dy, LayerPose.OPAQUE, true)
}

internal fun mirrorFrame(layer: NameplateBundle.Layer, frame: Int, mirrored: Boolean): Int =
    if (mirrored) frame - mirrorDelayOf(layer) * layer.step else frame

internal fun mirrorSeed(layer: NameplateBundle.Layer, seed: Int, mirrored: Boolean): Int =
    if (mirrored && mirrorSeedOf(layer)) NameplateHash.derive(seed, SALT_MIRROR) else seed

internal fun mirrorsOf(layer: NameplateBundle.Layer): Boolean = when (val content = layer.content) {
    is LayerContent.Sprite -> content.mirror
    is MaterialLayer -> content.mirror
    else -> false
}

internal fun mirrorDelayOf(layer: NameplateBundle.Layer): Int = when (val content = layer.content) {
    is LayerContent.Sprite -> content.mirrorDelay
    is MaterialLayer -> content.mirrorDelay
    else -> 0
}

internal fun mirrorSeedOf(layer: NameplateBundle.Layer): Boolean = when (val content = layer.content) {
    is LayerContent.Sprite -> content.mirrorSeed
    is MaterialLayer -> content.mirrorSeed
    else -> false
}

private fun multiply(a: Int, b: Int): Int = (a * b + OPACITY_HALF) / LayerPose.OPAQUE

private fun scheduledChannels(target: PoseTarget, slot: Long): ChannelTracks? =
    if (target.schedule == null || slot == NO_EVENT) null else target.schedule.events[eventOf(slot)].channels(target.id)

internal inline fun channelAt(
    triggers: Triggers,
    targetId: String,
    event: ChannelTracks?,
    loop: ChannelTracks?,
    state: TagState,
    time: Int,
    loopTime: Int,
    local: Int,
    molang: MolangFrame?,
    default: Int,
    select: (ChannelTracks) -> Track?,
): Int {
    val reacted = triggered(triggers, targetId, state, time, loopTime, molang, select)
    if (reacted != UNSET) return reacted

    val scheduled = event?.let { select(it) }
    val looped = loop?.let { select(it) }
    return when {
        scheduled != null -> scheduled.at(local, molang)
        looped != null -> looped.at(loopTime, molang)
        else -> default
    }
}
