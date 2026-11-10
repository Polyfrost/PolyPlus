package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash
import org.polyfrost.polyplus.nameplate.engine.internal.track.Schedule

private const val SALT_CHANCE = 1

private const val SALT_PICK = 2

private const val SALT_START = 3

private const val CHANCE_SHIFT = 8

private const val INDEX_SHIFT = 32

internal const val NO_EVENT = -1L

internal fun eventOf(slot: Long): Int = (slot ushr INDEX_SHIFT).toInt()

internal fun localOf(slot: Long): Int = slot.toInt()

internal fun scheduledSlot(schedule: Schedule, frame: Int, seed: Int, step: Int): Long {
    val time = frame.floorDiv(step)
    val event = windowEvent(schedule, seed, time.floorDiv(schedule.window))
    if (event == NO_EVENT) return NO_EVENT

    val local = time.mod(schedule.window)
    val index = eventOf(event)
    val start = localOf(event)
    if (local < start || local >= start + schedule.events[index].length) return NO_EVENT
    return (index.toLong() shl INDEX_SHIFT) or (local - start).toLong()
}

internal fun windowEvent(schedule: Schedule, seed: Int, window: Int): Long {
    val hash = NameplateHash.hash(seed, schedule.idHash, window)
    if ((NameplateHash.derive(hash, SALT_CHANCE) ushr CHANCE_SHIFT) >= schedule.chance) return NO_EVENT

    var pick = (NameplateHash.derive(hash, SALT_PICK) ushr 1) % schedule.totalWeight
    var index = 0
    while (pick >= schedule.events[index].weight) {
        pick -= schedule.events[index].weight
        index++
    }
    val start = (NameplateHash.derive(hash, SALT_START) ushr 1) % (schedule.jitter + 1)
    return (index.toLong() shl INDEX_SHIFT) or start.toLong()
}
