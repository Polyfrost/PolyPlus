package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.GroupJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ScheduleJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.TimelineStepJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Group
import org.polyfrost.polyplus.nameplate.engine.internal.track.BundleMolang
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.Schedule
import org.polyfrost.polyplus.nameplate.engine.internal.track.ScheduledAnimation
import kotlin.math.roundToInt

internal object AnimationCompiler {
    const val LOOP = "loop"
    const val PER_PLAYER = "per_player"

    private class Member(val name: String, val schedule: ScheduleJson, val animation: AnimationJson)

    fun schedules(animations: Map<String, AnimationJson>, molang: BundleMolang): List<Schedule> {
        val groups = LinkedHashMap<String, MutableList<Member>>()
        for ((name, animation) in animations) {
            val schedule = animation.schedule ?: continue
            groups.getOrPut(schedule.id ?: name) { ArrayList() }.add(Member(name, schedule, animation))
        }
        return groups.map { (id, members) ->
            val timing = members.first().schedule
            Schedule(
                id = id,
                window = timing.window,
                jitter = timing.jitter,
                chance = (timing.chance * Schedule.CHANCE_ONE).roundToInt(),
                events = members.map { event(it, molang) },
            )
        }
    }

    fun timeline(timeline: List<TimelineStepJson>, cycle: Int): IntArray {
        val frames = IntArray(cycle)
        var at = 0
        for (held in timeline) {
            frames.fill(held.frame, at, at + held.hold)
            at += held.hold
        }
        return frames
    }

    fun groups(groups: List<GroupJson>?): List<Group> =
        groups.orEmpty().map { Group(it.id, it.parent, it.layers) }

    fun loop(animations: Map<String, AnimationJson>, molang: BundleMolang): Map<String, ChannelTracks> {
        val loop = animations[LOOP]?.takeIf { it.schedule == null && it.trigger == null } ?: return emptyMap()
        return tracksOf(loop, molang)
    }

    fun perPlayer(phase: JsonElement?): Boolean = (phase as? JsonPrimitive)?.takeIf { it.isString }?.content == PER_PLAYER

    fun tracksOf(animation: AnimationJson, molang: BundleMolang): Map<String, ChannelTracks> {
        val tracks = LinkedHashMap<String, ChannelTracks>()
        for ((target, channels) in animation.tracks) {
            ChannelKeys.compile(channels, molang)?.let { tracks[target] = it }
        }
        return tracks
    }

    private fun event(member: Member, molang: BundleMolang): ScheduledAnimation = ScheduledAnimation(
        name = member.name,
        weight = member.schedule.weight,
        length = member.animation.length ?: 1,
        tracks = tracksOf(member.animation, molang),
    )
}
