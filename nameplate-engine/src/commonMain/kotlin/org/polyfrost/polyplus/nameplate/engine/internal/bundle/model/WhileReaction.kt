package org.polyfrost.polyplus.nameplate.engine.internal.bundle.model

import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.internal.text.TextTrack
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.PaletteJson
import org.polyfrost.polyplus.nameplate.engine.internal.track.ReducedTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track

internal sealed interface Trigger {
    class While(val reaction: Reaction) : Trigger

    class Start(val reaction: Reaction) : Trigger

    class Stop(val reaction: Reaction) : Trigger

    class Appear(val seconds: Int) : Trigger

    data object Equip : Trigger
}

internal class WhileReaction(
    val name: String,
    val reaction: Reaction,
    private val tracks: Map<String, ChannelTracks>,
    val speeds: Map<String, Track> = emptyMap(),
    val palette: PaletteJson = emptyMap(),
    val dropsWhenReduced: Boolean = false,
    val text: TextTrack? = null,
) {
    private val held: Map<String, ChannelTracks> = tracks.mapValues { ReducedTracks.hold(it.value) }

    val targets: Set<String>
        get() = tracks.keys

    fun channels(targetId: String, reduced: Boolean = false): ChannelTracks? = when {
        !reduced -> tracks[targetId]
        dropsWhenReduced -> null
        else -> held[targetId]
    }
}
