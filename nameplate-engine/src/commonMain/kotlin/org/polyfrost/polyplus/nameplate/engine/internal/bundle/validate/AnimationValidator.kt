package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.polyfrost.polyplus.molang.MolangEnvironment
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.AdaptiveCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.AnimationCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.ChannelKeys
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.FrameKeys
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.MaterialParamValidator
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.PaletteTracks
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.ReactionPaletteValidator
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.TriggerCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.EmitterLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ScheduleJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.TimelineStepJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.BADGE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PLATE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Trigger
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.issue.keyValues
import org.polyfrost.polyplus.nameplate.engine.internal.text.TEXT_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.track.BundleMolang
import org.polyfrost.polyplus.nameplate.engine.internal.track.MaterialParamTracks

internal class AnimationValidator(
    private val manifest: ManifestJson,
    private val layers: Map<String, LayerFileJson>,
    private val sprites: Map<String, SpriteFileJson>,
    private val report: IssueSink,
    private val tones: Map<String, Int>? = null,
) {
    private val steps = manifest.layers.associate { it.id to it.step }
    private val groupIds = manifest.groups.orEmpty().mapTo(HashSet()) { it.id }
    private val environment = BundleMolang.channelEnvironment(manifest)
    private val tracks = TrackValidator(layers, sprites, report, environment)
    private val scheduleOfLayer = HashMap<String, String>()
    private val firstOfSchedule = HashMap<String, Pair<String, ScheduleJson>>()
    private val stepOfSchedule = HashMap<String, Int>()
    private val triggers = TriggerValidator(manifest.loop, report)
    private val params = MaterialParamValidator(manifest, layers, report)

    fun validate() {
        manifest.phase?.let { phase ->
            if ((phase as? JsonPrimitive)?.takeIf { it.isString }?.content != AnimationCompiler.PER_PLAYER) {
                report("$MANIFEST#phase", "must be \"${AnimationCompiler.PER_PLAYER}\"")
            }
        }
        GroupValidator(manifest, report).validate()
        for ((id, layer) in layers) {
            if (layer !is SpriteLayerJson) continue
            val path = BundleFiles.layerPath(id)
            layer.timeline?.let { checkTimeline("$path#timeline", it, id, layer) }
            report.ensure(layer.mirrorDelay >= 0, "$path#mirrorDelay", "must be 0 or more")
        }
        for ((name, animation) in manifest.animations) {
            checkAnimation("$MANIFEST#animations.$name", name, animation)
        }
    }

    private fun checkTimeline(path: String, timeline: List<TimelineStepJson>, id: String, layer: SpriteLayerJson) {
        val frames = BundleFiles.frameCount(sprites[layer.sprite])
        val cycle = manifest.loop / (steps[id] ?: 1).coerceAtLeast(1)
        var total = 0L
        for ((i, held) in timeline.withIndex()) {
            if (held.hold < 1) {
                report("$path[$i].hold", "must be at least 1")
            } else {
                total += held.hold
            }
            if (frames != null && held.frame !in 0 until frames) {
                report("$path[$i].frame", "must be 0 to ${frames - 1}")
            }
        }
        if (total != cycle.toLong()) {
            report(path, "holds must add up to the layer's $cycle-step cycle")
        }
    }

    private fun checkAnimation(path: String, name: String, animation: AnimationJson) {
        val schedule = animation.schedule
        val trigger = animation.trigger
        if (trigger != null && (schedule != null || name == AnimationCompiler.LOOP)) {
            report("$path.trigger", FrameKeys.UNSUPPORTED)
        }
        if (animation.reduced != null && (trigger == null || schedule != null || name == AnimationCompiler.LOOP)) {
            report.issue("$path.reduced", "only while reactions take reduced", IssueCode.REDUCED_WHILE)
        }
        when {
            name == AnimationCompiler.LOOP -> {
                if (schedule != null) {
                    report(path, "the loop animation takes no schedule or trigger")
                }
                checkTracks(path, animation, null)
            }
            trigger != null && schedule == null -> triggers.validate(path, animation, trigger)?.let { length ->
                checkTracks(path, animation, null, length, palette = false, triggered = true)
                ReactionPaletteValidator(manifest, tones, report).validate(
                    path,
                    name,
                    animation,
                    length,
                    oneShot = TriggerCompiler.parse(trigger, path) { } !is Trigger.While,
                )
            }
            schedule == null -> report(path, FrameKeys.UNSUPPORTED)
            else -> checkScheduled(path, name, animation, schedule)
        }
    }

    private fun checkScheduled(path: String, name: String, animation: AnimationJson, schedule: ScheduleJson) {
        val id = schedule.id ?: name
        if (!BundleFiles.isSpriteId(id)) {
            report(if (schedule.id == null) path else "$path.schedule.id", "schedule id must be 1 to 64 of a-z, 0-9 and _")
        }
        checkSchedule("$path.schedule", schedule)
        checkLength(path, animation.length, schedule)
        checkSharing("$path.schedule", id, name, schedule)
        checkTracks(path, animation, id)
    }

    private fun checkTracks(
        path: String,
        animation: AnimationJson,
        scheduleId: String?,
        length: Int? = if (scheduleId == null) manifest.loop else animation.length,
        palette: Boolean = scheduleId == null,
        triggered: Boolean = false,
    ) {
        for ((layerId, channels) in animation.tracks) {
            val trackPath = "$path.tracks.$layerId"
            if (PaletteTracks.isPaletteTrack(layerId)) {
                if (!palette && !triggered) {
                    report(trackPath, FrameKeys.UNSUPPORTED)
                }
                continue
            }
            if (layerId == BADGE_TARGET) {
                checkBadge(trackPath, channels, scheduleId, length)
                continue
            }
            if (layerId == PLATE_TARGET) {
                checkPlate(trackPath, channels, scheduleId, length)
                continue
            }
            if (layerId == TEXT_TARGET) continue
            if (!checkTarget(trackPath, layerId, scheduleId)) continue
            for ((channel, element) in channels) {
                val channelPath = "$trackPath.$channel"
                when {
                    channel in ChannelKeys.CHANNELS -> tracks.validate(channelPath, channel, element, layerId, length)
                    channel == AdaptiveCompiler.BURST && layers[layerId] is EmitterLayerJson ->
                        AdaptiveValidator.checkBurst(channelPath, element, length, report)
                    channel == TriggerCompiler.SPEED && triggered -> checkSpeed(channelPath, layerId, element, length)
                    channel == TriggerCompiler.SPEED -> report.issue(
                        channelPath,
                        "speed tracks only work in reactions and one-shots; set the path's own speed in its layer",
                        IssueCode.SPEED_TRIGGER,
                    )
                    MaterialParamTracks.isChannel(channel) ->
                        params.validate(channelPath, layerId, channel, element, length, scheduleId != null)
                    else -> report(channelPath, FrameKeys.UNSUPPORTED)
                }
            }
        }
    }

    private fun checkBadge(path: String, channels: Map<String, JsonElement>, scheduleId: String?, length: Int?) {
        if (scheduleId != null) {
            report.issue(path, "the badge takes loop, reaction and one-shot tracks, not scheduled ones", IssueCode.BADGE_SCHEDULE)
            return
        }
        for ((channel, element) in channels) {
            val channelPath = "$path.$channel"
            if (channel !in BADGE_CHANNELS) {
                report.issue(channelPath, "the badge takes position, opacity and visible tracks", IssueCode.BADGE_CHANNEL)
                continue
            }
            var valid = true
            val validator = TrackValidator(layers, sprites, report.onIssue { valid = false }, environment)
            validator.validate(channelPath, channel, element, BADGE_TARGET, length)
            val parsed = if (valid) ChannelKeys.parse(channel, element, channelPath, { }) else null
            if (parsed != null && (parsed.molang.any { it != null } || parsed.secondMolang.any { it != null })) {
                report.issue(channelPath, "badge tracks take plain numbers, not Molang", IssueCode.BADGE_MOLANG)
            }
        }
    }

    private fun checkPlate(path: String, channels: Map<String, JsonElement>, scheduleId: String?, length: Int?) {
        if (scheduleId != null) {
            checkDriver(path, PLATE_TARGET, scheduleId, isLayer = false)
        }
        for ((channel, element) in channels) {
            val channelPath = "$path.$channel"
            if (channel == ChannelKeys.POSITION) {
                tracks.validate(channelPath, channel, element, PLATE_TARGET, length)
            } else {
                report.issue(channelPath, "the whole plate takes position tracks only", IssueCode.PLATE_CHANNEL)
            }
        }
    }

    private fun checkSpeed(path: String, layerId: String, element: JsonElement, length: Int?) {
        val route = (layers[layerId] as? SpriteLayerJson)?.path
        if (route == null) {
            report.issue(path, "speed tracks need a sprite layer on a path", IssueCode.SPEED_PATH)
            return
        }
        val laps = route.laps
        if (laps != null) {
            report.issue(path, "speed tracks can't change a path timed in laps", IssueCode.SPEED_PATH, issueValues("laps" to laps))
            return
        }
        AdaptiveValidator.checkTriggeredSpeed(path, element, length, report)
    }

    private fun checkSchedule(path: String, schedule: ScheduleJson) {
        report.ensure(schedule.window >= 1, "$path.window", "must be at least 1")
        report.ensure(schedule.jitter >= 0, "$path.jitter", "must be 0 or more")
        report.ensure(schedule.chance in 0.0..1.0, "$path.chance", "must be 0 to 1")
        report.ensure(
            schedule.weight in 1..BundleLimits.MAX_SCHEDULE_WEIGHT,
            "$path.weight",
            "must be 1 to ${BundleLimits.MAX_SCHEDULE_WEIGHT}",
        )
    }

    private fun checkLength(path: String, length: Int?, schedule: ScheduleJson) {
        when {
            length == null -> report("$path.length", "a scheduled animation needs a length in steps")
            length < 1 -> report("$path.length", "must be at least 1")
            length.toLong() + schedule.jitter > schedule.window ->
                report("$path.length", "length + jitter must fit inside the window (${schedule.window})")
        }
    }

    private fun checkSharing(path: String, id: String, name: String, schedule: ScheduleJson) {
        val (firstName, first) = firstOfSchedule.getOrPut(id) { name to schedule }
        val mismatch = "must match '$firstName', which shares schedule '$id'"
        report.ensure(first.window == schedule.window, "$path.window", mismatch)
        report.ensure(first.jitter == schedule.jitter, "$path.jitter", mismatch)
        report.ensure(first.chance == schedule.chance, "$path.chance", mismatch)
    }

    private fun checkTarget(path: String, id: String, scheduleId: String?): Boolean {
        val isLayer = id in layers
        if (!isLayer && id !in groupIds) {
            report(path, "must name a layer or a group")
            return false
        }
        if (scheduleId != null) {
            checkDriver(path, id, scheduleId, isLayer)
        }
        return true
    }

    private fun checkDriver(path: String, id: String, scheduleId: String, isLayer: Boolean) {
        val driver = scheduleOfLayer.getOrPut(id) { scheduleId }
        if (driver != scheduleId) {
            report(path, "is already driven by schedule '$driver'; a layer or group takes one schedule")
        }
        if (isLayer) {
            val step = steps[id] ?: 1
            val shared = stepOfSchedule.getOrPut(scheduleId) { step }
            if (shared != step) {
                report(path, "has step $step, but the other layers of schedule '$scheduleId' have step $shared")
            }
        }
    }

    private companion object {
        const val MANIFEST = BundleFiles.MANIFEST

        val BADGE_CHANNELS: Set<String> = setOf(ChannelKeys.POSITION, ChannelKeys.OPACITY, ChannelKeys.VISIBLE)
    }
}

internal class TrackValidator(
    private val layers: Map<String, LayerFileJson>,
    private val sprites: Map<String, SpriteFileJson>,
    private val report: IssueSink,
    private val environment: MolangEnvironment,
) {
    fun validate(path: String, channel: String, element: JsonElement, target: String, length: Int?) {
        val sprite = layers[target] as? SpriteLayerJson
        if (channel == ChannelKeys.FRAME && sprite == null) {
            report(path, "frame tracks need a sprite layer")
            return
        }

        val keys = ChannelKeys.parse(channel, element, path, report, environment) ?: return
        if (keys.steps.isEmpty()) {
            report(path, "needs at least one key")
            return
        }

        val frames = sprite?.let { BundleFiles.frameCount(sprites[it.sprite]) }
        for (i in keys.steps.indices) {
            checkStep("$path[$i]", keys.steps, i, length)
            if (channel == ChannelKeys.FRAME && frames != null && keys.molang[i] == null && keys.values[i] !in 0 until frames) {
                report("$path[$i]", "frame must be 0 to ${frames - 1}")
            }
        }
    }

    private fun checkStep(path: String, steps: IntArray, index: Int, length: Int?) {
        val step = steps[index]
        when {
            index == 0 && step != 0 -> report.issue(path, "the first key must be at step 0", IssueCode.KEY_START, keyValues(step, length))
            index > 0 && step <= steps[index - 1] ->
                report.issue(path, "keys must be in increasing step order", IssueCode.KEY_ORDER, keyValues(step, length))
            step < 0 || (length != null && step >= length) ->
                report.issue(path, "step must be inside the animation's length", IssueCode.KEY_RANGE, keyValues(step, length))
            step > BundleLimits.MAX_TRACK_STEP -> report(path, "step must be at most ${BundleLimits.MAX_TRACK_STEP}")
        }
    }
}

internal class TriggerValidator(private val loop: Int, private val report: IssueSink) {
    fun validate(path: String, animation: AnimationJson, element: JsonElement): Int? {
        val trigger = TriggerCompiler.parse(element, "$path.trigger", report) ?: return null
        animation.reduced?.let { reduced ->
            when {
                trigger !is Trigger.While -> report.issue(
                    "$path.reduced",
                    "only while reactions take reduced; one-shots never play when motion is reduced",
                    IssueCode.REDUCED_WHILE,
                )
                reduced != TriggerCompiler.REDUCED_AUTO && reduced != TriggerCompiler.REDUCED_DROP ->
                    report.issue(
                        "$path.reduced",
                        "must be \"${TriggerCompiler.REDUCED_AUTO}\" or \"${TriggerCompiler.REDUCED_DROP}\"",
                        IssueCode.REDUCED_CHOICE,
                    )
            }
        }
        if (trigger is Trigger.While) {
            if (animation.length != null) {
                report("$path.length", "a while reaction plays in the loop's frames; it takes no length")
            }
            if (animation.restart != null) {
                report("$path.restart", "only one-shots take restart")
            }
            return loop
        }

        val length = animation.length
        return when {
            length == null -> failed("$path.length", "a one-shot needs a length in frames")
            length !in 1..BundleLimits.MAX_LOOP -> failed("$path.length", "must be 1 to ${BundleLimits.MAX_LOOP} frames")
            else -> length
        }
    }

    private fun failed(path: String, message: String): Int? {
        report(path, message)
        return null
    }
}
