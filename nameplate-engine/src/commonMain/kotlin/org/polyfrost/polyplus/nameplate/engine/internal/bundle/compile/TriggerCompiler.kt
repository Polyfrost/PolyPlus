package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.polyfrost.polyplus.nameplate.engine.api.bundle.OneShot
import org.polyfrost.polyplus.nameplate.engine.api.bundle.Triggers
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.internal.anim.triggered
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Trigger
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.WhileReaction
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.track.BundleMolang
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track

internal object TriggerCompiler {
    const val DEFAULT_APPEAR_SECONDS = 5
    const val MAX_APPEAR_SECONDS = 30

    private const val EQUIP = "equip"
    private const val APPEAR = "appear"
    private const val REACTION = "reaction"
    private const val ON = "on"
    const val SPEED = "speed"
    const val REDUCED_AUTO = "auto"
    const val REDUCED_DROP = "drop"
    private const val SHAPE =
        "must be \"equip\", \"appear\", { \"appear\": seconds } or { \"reaction\": name, \"on\": \"while\" | \"start\" | \"stop\" }"

    fun parse(element: JsonElement, path: String, report: IssueSink): Trigger? = when (element) {
        is JsonPrimitive -> when (element.takeIf { it.isString }?.content) {
            EQUIP -> Trigger.Equip
            APPEAR -> Trigger.Appear(DEFAULT_APPEAR_SECONDS)
            else -> failed(path, SHAPE, report)
        }
        is JsonObject -> parseObject(element, path, report)
        else -> failed(path, SHAPE, report)
    }

    fun compile(animations: Map<String, AnimationJson>, molang: BundleMolang): Triggers {
        val oneShots = ArrayList<OneShot>()
        val whiles = ArrayList<WhileReaction>()
        for ((name, trigger) in triggered(animations)) {
            val animation = animations.getValue(name)
            val tracks = tracksOf(animation, molang)
            val speeds = speedsOf(animation)
            val palette = animation.tracks.filterKeys(PaletteTracks::isPaletteTrack)
            val text = TextTracks.compile(animation, molang.channelEnvironment.paramNames)
            if (trigger is Trigger.While) {
                val drops = animation.reduced == REDUCED_DROP
                whiles.add(WhileReaction(name, trigger.reaction, tracks, speeds, palette, drops, text))
            } else {
                oneShots.add(OneShot(name, trigger, animation.length ?: 1, animation.restart ?: false, tracks, speeds, palette, text))
            }
        }

        return if (oneShots.isEmpty() && whiles.isEmpty()) Triggers.NONE else Triggers(oneShots, whiles)
    }

    fun triggered(animations: Map<String, AnimationJson>): Map<String, Trigger> {
        val found = LinkedHashMap<String, Trigger>()
        for ((name, animation) in animations) {
            val element = animation.trigger ?: continue
            if (name == AnimationCompiler.LOOP || animation.schedule != null) continue
            parse(element, "") { }?.let { found[name] = it }
        }
        return found
    }

    fun oneShotIndices(animations: Map<String, AnimationJson>): Map<String, Int> {
        val indices = LinkedHashMap<String, Int>()
        for ((name, trigger) in triggered(animations)) {
            if (trigger !is Trigger.While) {
                indices[name] = indices.size
            }
        }
        return indices
    }

    private fun parseObject(element: JsonObject, path: String, report: IssueSink): Trigger? {
        val reaction = element[REACTION]
        val appear = element[APPEAR]
        return when {
            reaction != null && appear != null -> failed(path, "takes either reaction or appear, not both", report)
            reaction != null -> reaction(reaction, element[ON], path, report)
            appear != null -> appear(appear, path, report)
            else -> failed(path, SHAPE, report)
        }
    }

    private fun reaction(element: JsonElement, on: JsonElement?, path: String, report: IssueSink): Trigger? {
        val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        val reaction = name?.let(Reaction::of)
        if (reaction == null) {
            val known = Reaction.entries.joinToString { it.id }
            report("$path.$REACTION", if (name == null) "must be a reaction name" else "no reaction '$name'; reactions are $known")
        }
        val mode = (on as? JsonPrimitive)?.takeIf { it.isString }?.content
        val trigger = when (mode) {
            "while" -> reaction?.let { Trigger.While(it) }
            "start" -> reaction?.let { Trigger.Start(it) }
            "stop" -> reaction?.let { Trigger.Stop(it) }
            else -> failed("$path.$ON", "must be \"while\", \"start\" or \"stop\"", report)
        }

        return trigger
    }

    private fun appear(element: JsonElement, path: String, report: IssueSink): Trigger? {
        val seconds = ChannelKeys.wholeNumber(element)
        if (seconds == null || seconds !in 1..MAX_APPEAR_SECONDS) {
            return failed("$path.$APPEAR", "must be 1 to $MAX_APPEAR_SECONDS whole seconds", report)
        }

        return Trigger.Appear(seconds)
    }

    private fun speedsOf(animation: AnimationJson): Map<String, Track> {
        val speeds = LinkedHashMap<String, Track>()
        for ((target, channels) in animation.tracks) {
            channels[SPEED]?.let { speeds[target] = AdaptiveCompiler.speedTrack(it) }
        }
        return speeds
    }

    private fun tracksOf(animation: AnimationJson, molang: BundleMolang): Map<String, ChannelTracks> {
        val tracks = LinkedHashMap<String, ChannelTracks>()
        for ((target, channels) in animation.tracks) {
            ChannelKeys.compile(channels, molang)?.let { tracks[target] = it }
        }

        return tracks
    }

    private fun failed(path: String, message: String, report: IssueSink): Trigger? {
        report(path, message)
        return null
    }
}
