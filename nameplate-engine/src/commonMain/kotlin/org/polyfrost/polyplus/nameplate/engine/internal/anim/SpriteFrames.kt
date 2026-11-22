package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangFrame
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track

internal fun frameAt(
    bundle: NameplateBundle,
    layer: NameplateBundle.Layer,
    frame: Int,
    seed: Int,
    mirrored: Boolean = false,
    state: TagState = TagState.NONE,
    molang: MolangFrame? = null,
): Int {
    val sprite = layer.content as? LayerContent.Sprite ?: return 0
    val shifted = mirrorFrame(layer, frame, mirrored)
    val time = shifted - phaseOffset(bundle, seed)
    val reacted = triggered(bundle.triggers, layer.id, state, time, shifted.mod(bundle.loop), molang) { it.frame }
    if (reacted != UNSET) return clampFrame(sprite, reacted)

    val rolled = mirrorSeed(layer, seed, mirrored)
    val shown = scheduledFrame(bundle, layer, shifted, rolled, molang)
        ?: loopFrame(bundle, layer, shifted, molang)
        ?: normalFrame(bundle, layer, sprite, shifted)
    return clampFrame(sprite, shown)
}

internal fun states(bundle: NameplateBundle, layer: NameplateBundle.Layer): Set<Int> {
    val sprite = layer.content as? LayerContent.Sprite ?: return setOf(0)
    val shown = HashSet<Int>()
    val looped = bundle.loopChannels[layer.id]?.frame
    if (looped != null) {
        addValues(looped, sprite, shown)
    } else {
        val cycle = bundle.loop / layer.step
        for (step in 0 until cycle) {
            shown.add(if (sprite.hasTimeline) sprite.timelineFrame(step) else step % sprite.frames.size)
        }
    }
    bundle.scheduleOf(layer.id)?.let { schedule ->
        for (event in schedule.events) {
            event.track(layer.id)?.let { addValues(it, sprite, shown) }
        }
    }
    bundle.triggers.channelsOf(layer.id).forEach { tracks -> tracks.frame?.let { addValues(it, sprite, shown) } }
    return adaptiveStates(sprite, shown)
}

internal fun clampFrame(sprite: LayerContent.Sprite, frame: Int): Int =
    if (sprite.frames.isEmpty()) frame else minOf(frame, sprite.frames.size - 1)

private fun addValues(track: Track, sprite: LayerContent.Sprite, into: MutableSet<Int>) {
    if (track.usesMolang) {
        sprite.frames.indices.forEach(into::add)
    } else {
        track.values().forEach(into::add)
    }
}

private fun normalFrame(bundle: NameplateBundle, layer: NameplateBundle.Layer, sprite: LayerContent.Sprite, frame: Int): Int {
    val step = frame.mod(bundle.loop) / layer.step
    return if (sprite.hasTimeline) sprite.timelineFrame(step) else step % sprite.frames.size
}

private fun loopFrame(bundle: NameplateBundle, layer: NameplateBundle.Layer, frame: Int, molang: MolangFrame?): Int? =
    bundle.loopChannels[layer.id]?.frame?.at(frame.mod(bundle.loop), molang)

private fun scheduledFrame(bundle: NameplateBundle, layer: NameplateBundle.Layer, frame: Int, seed: Int, molang: MolangFrame?): Int? {
    val schedule = bundle.scheduleOf(layer.id) ?: return null
    val slot = scheduledSlot(schedule, frame, seed, layer.step)
    if (slot == NO_EVENT) return null
    return schedule.events[eventOf(slot)].track(layer.id)?.at(localOf(slot), molang)
}
