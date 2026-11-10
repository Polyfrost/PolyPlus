package org.polyfrost.polyplus.nameplate.engine.internal.track

import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash

internal class Schedule(
    val id: String,
    val window: Int,
    val jitter: Int,
    val chance: Int,
    val events: List<ScheduledAnimation>,
) {
    val idHash: Int = NameplateHash.text(id)
    val totalWeight: Int = events.sumOf { it.weight }

    companion object {
        const val CHANCE_ONE = 1 shl 24
    }
}

internal class ScheduledAnimation(
    val name: String,
    val weight: Int,
    val length: Int,
    private val tracks: Map<String, ChannelTracks>,
) {
    val layers: Set<String>
        get() = tracks.keys

    fun track(layerId: String): Track? = tracks[layerId]?.frame

    fun channels(targetId: String): ChannelTracks? = tracks[targetId]
}
