package org.polyfrost.polyplus.nameplate.engine.api.bundle

import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Trigger
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.WhileReaction
import org.polyfrost.polyplus.nameplate.engine.internal.text.TextTrack
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.PaletteJson
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track
import kotlin.concurrent.Volatile

class OneShot internal constructor(
    val name: String,
    internal val trigger: Trigger,
    val length: Int,
    val restart: Boolean,
    private val tracks: Map<String, ChannelTracks>,
    internal val speeds: Map<String, Track> = emptyMap(),
    internal val palette: PaletteJson = emptyMap(),
    internal val text: TextTrack? = null,
) {
    val targets: Set<String>
        get() = tracks.keys

    internal fun channels(targetId: String): ChannelTracks? = tracks[targetId]
}

class Triggers internal constructor(val oneShots: List<OneShot>, internal val whiles: List<WhileReaction>) {
    private val whilesByTarget: Map<String, Array<WhileReaction>> = buildMap {
        val grouped = LinkedHashMap<String, MutableList<WhileReaction>>()
        for (reaction in whiles) {
            for (target in reaction.targets) {
                grouped.getOrPut(target) { ArrayList() }.add(reaction)
            }
        }
        for ((target, list) in grouped) {
            put(target, list.toTypedArray())
        }
    }

    internal val targets: Set<String> = oneShots.flatMapTo(HashSet()) { it.targets } + whilesByTarget.keys

    internal val speedTargets: List<String> =
        (oneShots.flatMap { it.speeds.keys } + whiles.flatMap { it.speeds.keys }).distinct()

    internal val hasPalette: Boolean = oneShots.any { it.palette.isNotEmpty() } || whiles.any { it.palette.isNotEmpty() }

    internal val hasText: Boolean = oneShots.any { it.text != null } || whiles.any { it.text != null }

    @Volatile
    internal var paletteViews: Array<PaletteView?>? = null

    @Volatile
    internal var ends: IntArray? = null

    val isEmpty: Boolean
        get() = oneShots.isEmpty() && whiles.isEmpty()

    val reactions: Set<Reaction>
        get() = buildSet {
            whiles.forEach { add(it.reaction) }
            for (shot in oneShots) {
                when (val trigger = shot.trigger) {
                    is Trigger.Start -> add(trigger.reaction)
                    is Trigger.Stop -> add(trigger.reaction)
                    else -> Unit
                }
            }
        }

    fun indexOf(name: String): Int = oneShots.indexOfFirst { it.name == name }

    internal fun speedIndex(layerId: String): Int = speedTargets.indexOf(layerId)

    internal fun whilesOf(targetId: String): Array<WhileReaction> = whilesByTarget[targetId] ?: NO_WHILES

    internal fun channelsOf(targetId: String): List<ChannelTracks> =
        oneShots.mapNotNull { it.channels(targetId) } + whiles.mapNotNull { it.channels(targetId) }

    companion object {
        val NONE: Triggers = Triggers(emptyList(), emptyList())

        private val NO_WHILES: Array<WhileReaction> = emptyArray()
    }
}

internal class PaletteView(val tracks: NameplateBundle, val slots: IntArray)
