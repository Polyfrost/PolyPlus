package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.anim.Particles
import org.polyfrost.polyplus.nameplate.engine.internal.anim.PathMotion
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.AdaptiveCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.ChannelKeys
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BreakpointJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.EmitterLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.MaterialLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.PathJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.PathStopJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.RepeatJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.WalkersJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Edge
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeFollow
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PathEnd
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.issue.keyValues
import org.polyfrost.polyplus.nameplate.engine.internal.layout.Anchor
import org.polyfrost.polyplus.nameplate.engine.internal.track.Interpolation

internal object AdaptiveLimits {
    const val MAX_PATH_POINTS = 32
    const val MAX_PAUSE = 600
    const val MAX_SPEED = 16.0
    const val MAX_WALKERS = 32
    const val MAX_SPACING = 256
    const val MAX_LAPS = 64
    const val MAX_STOPS = 32
    const val MAX_REPEATS = 64
    const val MAX_BREAKPOINTS = 8
    const val MAX_WIDTH = 4096
    const val MAX_POOL = 8
    const val MAX_SPAWN_COUNT = 32
    const val MAX_LIFETIME = 240
    const val MAX_PARTICLE_SPEED = 16.0
    const val MAX_GRAVITY = 4.0
    const val MAX_WOBBLE = 8
    const val MAX_FADE_STEPS = 16
}

internal class AdaptiveValidator(
    private val manifest: ManifestJson,
    private val layers: Map<String, LayerFileJson>,
    private val sprites: Map<String, SpriteFileJson>,
    private val report: IssueSink,
) {
    private val steps = manifest.layers.associate { it.id to it.step }

    fun validate() {
        for ((id, layer) in layers) {
            val path = BundleFiles.layerPath(id)
            when (layer) {
                is SpriteLayerJson -> checkSprite(path, id, layer)
                is EmitterLayerJson -> checkEmitter(path, layer)
                is BodyLayerJson, is MaterialLayerJson -> Unit
            }
        }
        for ((i, group) in manifest.groups.orEmpty().withIndex()) {
            group.breakpoints?.let { checkBreakpoints("${BundleFiles.MANIFEST}#groups[$i].breakpoints", it, null) }
        }
    }

    private fun checkSprite(path: String, id: String, layer: SpriteLayerJson) {
        val walk = layer.path
        val repeat = layer.repeat
        if (walk != null) {
            checkPath("$path#path", walk, layer, manifest.loop / (steps[id] ?: 1).coerceAtLeast(1))
        }
        layer.walkers?.let { walkers ->
            if (walk == null) {
                report("$path#walkers", "walkers need a path")
            }
            report.ensure(
                walkers.count in 1..AdaptiveLimits.MAX_WALKERS,
                "$path#walkers.count",
                "must be 1 to ${AdaptiveLimits.MAX_WALKERS}",
            )
            report.ensure(
                walkers.spacing in 0..AdaptiveLimits.MAX_SPACING,
                "$path#walkers.spacing",
                "must be 0 to ${AdaptiveLimits.MAX_SPACING} px",
            )
            if (walkers.even) {
                checkEven("$path#walkers.even", walkers, walk)
            }
        }
        if (repeat != null) {
            checkRepeat("$path#repeat", repeat)
        }
        if (layer.mirror && (walk != null || repeat != null)) {
            report("$path#mirror", "a layer on a path or repeat takes no mirror")
        }
        if (walk != null && repeat != null) {
            report("$path#repeat", "a layer takes a path or a repeat, not both")
        }
        layer.breakpoints?.let { checkBreakpoints("$path#breakpoints", it, layer) }
    }

    private fun checkPath(path: String, walk: PathJson, layer: SpriteLayerJson, cycle: Int) {
        val preset = walk.preset
        val points = walk.points
        when {
            (preset == null) == (points == null) -> report(path, "needs exactly one of preset or points")
            preset != null && preset !in AdaptiveCompiler.PRESETS ->
                report("$path.preset", "must be one of ${AdaptiveCompiler.PRESETS.keys.joinToString()}")
            points != null && points.size !in 2..AdaptiveLimits.MAX_PATH_POINTS ->
                report("$path.points", "must hold 2 to ${AdaptiveLimits.MAX_PATH_POINTS} points")
        }
        for ((i, point) in points.orEmpty().withIndex()) {
            if (Anchor.named(point.anchor) == null) {
                report("$path.points[$i].anchor", "must be one of ${BundleValidator.ANCHORS.joinToString()}")
            }
            BundleValidator.checkOffset("$path.points[$i].offset", point.offset, report)
            report.ensure(
                point.pause in 0..AdaptiveLimits.MAX_PAUSE,
                "$path.points[$i].pause",
                "must be 0 to ${AdaptiveLimits.MAX_PAUSE} steps",
            )
        }
        val keyed = checkSpeed("$path.speed", walk.speed, cycle)
        if (keyed && points.orEmpty().any { it.pause > 0 }) {
            report("$path.speed", "pauses need a constant speed")
        }
        if (PathEnd.entries.none { it.name.equals(walk.end, ignoreCase = true) }) {
            report("$path.end", "must be wrap, bounce or stop")
        }
        walk.frames?.let { checkFacings("$path.frames", it, layer) }
        walk.follow?.let { follow ->
            when {
                EdgeFollow.named(follow) == null -> report("$path.follow", "must be outside, line or inside")
                preset == null -> report("$path.follow", "following the body's edge only works on the ready-made top, bottom and all-around routes")
            }
        }
        val timing = report.coded(IssueCode.PATH_TIMING)
        walk.stops?.let { checkStops("$path.stops", it, walk, keyed, timing) }
        walk.laps?.let { checkLaps("$path.laps", it, walk, cycle, timing) }
    }

    private fun checkEven(path: String, walkers: WalkersJson, walk: PathJson?) {
        val timing = report.coded(IssueCode.PATH_TIMING)
        if (walkers.spacing != 0) {
            timing(path, "evenly spaced walkers take no spacing")
        }
        if (walk != null && isStop(walk)) {
            timing(path, "evenly spaced walkers need the path to wrap or bounce")
        }
    }

    private fun checkStops(path: String, stops: List<PathStopJson>, walk: PathJson, keyed: Boolean, report: IssueSink) {
        if (walk.points != null) {
            report(path, "stops only work on the ready-made top, bottom and all-around routes; a custom route pauses on its points")
        }
        if (keyed) {
            report(path, "stops need a constant speed")
        }
        if (stops.size !in 1..AdaptiveLimits.MAX_STOPS) {
            report.issue(
                path,
                "must hold 1 to ${AdaptiveLimits.MAX_STOPS} stops",
                IssueCode.PATH_TIMING,
                issueValues("count" to stops.size, "limit" to AdaptiveLimits.MAX_STOPS),
            )
        }
        var last = -1.0
        for ((i, stop) in stops.withIndex()) {
            when {
                stop.at !in 0.0..1.0 -> report("$path[$i].at", "must be 0 to 1")
                stop.at <= last -> report("$path[$i].at", "must be above the stop before it")
            }
            report.ensure(stop.pause in 1..AdaptiveLimits.MAX_PAUSE, "$path[$i].pause", "must be 1 to ${AdaptiveLimits.MAX_PAUSE} steps")
            last = stop.at
        }
    }

    private fun checkLaps(path: String, laps: Int, walk: PathJson, cycle: Int, report: IssueSink) {
        if (laps !in 1..AdaptiveLimits.MAX_LAPS) {
            report.issue(
                path,
                "must be 1 to ${AdaptiveLimits.MAX_LAPS}",
                IssueCode.PATH_TIMING,
                issueValues("laps" to laps, "limit" to AdaptiveLimits.MAX_LAPS),
            )
            return
        }
        if (walk.speed != null) {
            report(path, "laps set the speed, so a path takes laps or speed, not both")
        }
        if (isStop(walk)) {
            report(path, "laps need the path to wrap or bounce")
        }
        val waits = walk.stops.orEmpty().sumOf { it.pause } + walk.points.orEmpty().sumOf { it.pause }
        if (waits == 0) return

        val pauses = if (walk.end.equals(PathEnd.BOUNCE.name, ignoreCase = true)) 2 * waits else waits
        val lap = cycle / laps
        when {
            cycle % laps != 0 -> report.issue(
                path,
                "$laps laps must divide the layer's $cycle-step cycle evenly when the route waits",
                IssueCode.PATH_TIMING,
                issueValues("laps" to laps, "cycle" to cycle),
            )
            lap - pauses < 1 -> report.issue(
                path,
                "the waits add up to $pauses steps of a $lap-step lap, which leaves no time to move",
                IssueCode.PATH_TIMING,
                issueValues("pauses" to pauses, "lap" to lap),
            )
        }
    }

    private fun isStop(walk: PathJson): Boolean = walk.end.equals(PathEnd.STOP.name, ignoreCase = true)

    private fun checkSpeed(path: String, speed: JsonElement?, cycle: Int): Boolean {
        if (speed == null) return false
        if (speed !is JsonArray) {
            val value = (speed as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            if (value == null || AdaptiveCompiler.fixed(value, PathMotion.UNIT) < 1 || value > AdaptiveLimits.MAX_SPEED) {
                report(path, "must be more than 0 and at most ${AdaptiveLimits.MAX_SPEED} px per step, or a list of [step, speed] keys")
            }
            return false
        }

        if (speed.isEmpty()) {
            report(path, "needs at least one key")
        }
        checkSpeedKeys(path, speed, cycle, false, report)
        return true
    }

    private fun checkFacings(path: String, facings: Map<String, List<Int>>, layer: SpriteLayerJson) {
        val frames = frameCount(layer.sprite)
        if ("left" !in facings && "right" !in facings) {
            report(path, "needs left or right frames")
        }
        for ((facing, list) in facings) {
            if (facing !in PathMotion.FACINGS) {
                report("$path.$facing", "must be one of ${PathMotion.FACINGS.joinToString()}")
            }
            if (list.isEmpty() || list.size > BundleLimits.MAX_SPRITE_FRAMES) {
                report("$path.$facing", "must hold 1 to ${BundleLimits.MAX_SPRITE_FRAMES} frames")
            }
            if (frames != null && list.any { it !in 0 until frames }) {
                report("$path.$facing", "frames must be 0 to ${frames - 1}")
            }
        }
    }

    private fun checkRepeat(path: String, repeat: RepeatJson) {
        if (Edge.named(repeat.edge) == null) {
            report("$path.edge", "must be top, bottom, left or right")
        }
        report.ensure(repeat.spacing in 1..AdaptiveLimits.MAX_SPACING, "$path.spacing", "must be 1 to ${AdaptiveLimits.MAX_SPACING} px")
        report.ensure(repeat.min in 1..AdaptiveLimits.MAX_REPEATS, "$path.min", "must be 1 to ${AdaptiveLimits.MAX_REPEATS}")
        report.ensure(repeat.max in repeat.min..AdaptiveLimits.MAX_REPEATS, "$path.max", "must be min to ${AdaptiveLimits.MAX_REPEATS}")
    }

    private fun checkBreakpoints(path: String, breakpoints: List<BreakpointJson>, layer: SpriteLayerJson?) {
        if (breakpoints.isEmpty() || breakpoints.size > AdaptiveLimits.MAX_BREAKPOINTS) {
            report(path, "must hold 1 to ${AdaptiveLimits.MAX_BREAKPOINTS} ranges")
        }
        val frames = layer?.let { frameCount(it.sprite) }
        for ((i, entry) in breakpoints.withIndex()) {
            checkWidths("$path[$i]", entry, report)
            val sprite = entry.sprite ?: continue
            when {
                layer == null -> report("$path[$i].sprite", "only sprite layers swap sprites")
                sprite !in sprites -> report("$path[$i].sprite", "no sprite '$sprite'")
                frames != null && frameCount(sprite) != frames ->
                    report("$path[$i].sprite", "must have the same $frames frames as '${layer.sprite}'")
            }
        }
    }

    private fun checkEmitter(path: String, emitter: EmitterLayerJson) {
        EmitterValidator(path, emitter, layers, sprites, report.coded(IssueCode.EMITTER)).validate()
    }

    private fun frameCount(sprite: String): Int? = BundleFiles.frameCount(sprites[sprite])

    companion object {
        fun checkWidths(path: String, entry: BreakpointJson, report: IssueSink) {
            val max = entry.max
            if (entry.min !in 0..AdaptiveLimits.MAX_WIDTH || (max != null && max !in entry.min + 1..AdaptiveLimits.MAX_WIDTH)) {
                report(path, "needs 0 <= min < max <= ${AdaptiveLimits.MAX_WIDTH}")
            }
        }

        fun checkTriggeredSpeed(path: String, element: JsonElement, length: Int?, report: IssueSink) {
            val keys = element as? JsonArray
            if (keys == null || keys.isEmpty()) {
                report(path, "must be a list of [step, speed] keys")
                return
            }
            checkSpeedKeys(path, keys, length, true, report)
        }

        private fun checkSpeedKeys(path: String, keys: JsonArray, length: Int?, triggered: Boolean, report: IssueSink) {
            val limit = AdaptiveLimits.MAX_SPEED
            val shown = limit.toInt()
            var last = -1
            for ((i, entry) in keys.withIndex()) {
                val key = entry as? JsonArray
                val step = key?.getOrNull(0)?.let(ChannelKeys::wholeNumber)
                val value = (key?.getOrNull(1) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
                val mode =
                    key?.getOrNull(2)?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.let(Interpolation::parse) }
                when {
                    key == null || key.size !in 2..3 || step == null || value == null ->
                        report("$path[$i]", "must be [step, speed] or [step, speed, interpolation]")
                    key.size == 3 && mode == null -> report("$path[$i]", "interpolation must be step, linear or ease")
                    i == 0 && step != 0 ->
                        report.issue("$path[$i]", "the first key must be at step 0", IssueCode.KEY_START, keyValues(step, length))
                    step <= last ->
                        report.issue("$path[$i]", "keys must be in increasing step order", IssueCode.KEY_ORDER, keyValues(step, length))
                    length != null && step >= length ->
                        report.issue(
                            "$path[$i]",
                            "step must be inside ${if (triggered) "the animation's length" else "the layer's $length-step cycle"}",
                            IssueCode.KEY_RANGE,
                            keyValues(step, length),
                        )
                    triggered && (!value.isFinite() || value < -limit || value > limit) -> report.issue(
                        "$path[$i]",
                        "speed must be -$shown to $shown px per step; 0 stops the path and below 0 runs it backwards",
                        IssueCode.SPEED_RANGE,
                        issueValues("min" to -shown, "limit" to shown),
                    )
                    !triggered && (value < 0.0 || value > limit) -> report.issue(
                        "$path[$i]",
                        "speed must be 0 to $limit px per step",
                        IssueCode.SPEED_RANGE,
                        issueValues("min" to 0, "limit" to limit),
                    )
                }
                last = step ?: last
            }
        }

        fun checkBurst(
            path: String,
            element: JsonElement,
            length: Int?,
            report: IssueSink,
        ) {
            val keys = element as? JsonArray
            if (keys == null || keys.isEmpty()) {
                report(path, "must be a list of [step, count] keys")
                return
            }
            var last = -1
            for ((i, entry) in keys.withIndex()) {
                val key = entry as? JsonArray
                val step = key?.getOrNull(0)?.let(ChannelKeys::wholeNumber)
                val count = key?.getOrNull(1)?.let(ChannelKeys::wholeNumber)
                when {
                    key == null || key.size != 2 || step == null || count == null -> report("$path[$i]", "must be [step, count]")
                    step <= last ->
                        report.issue("$path[$i]", "keys must be in increasing step order", IssueCode.KEY_ORDER, keyValues(step, length))
                    length != null && step >= length ->
                        report.issue(
                            "$path[$i]",
                            "step must be inside the animation's length",
                            IssueCode.KEY_RANGE,
                            keyValues(step, length),
                        )
                    count !in 1..Particles.MAX_BURST -> report.issue(
                        "$path[$i]",
                        "count must be 1 to ${Particles.MAX_BURST}",
                        IssueCode.BURST_COUNT,
                        issueValues("count" to count, "limit" to Particles.MAX_BURST),
                    )
                }
                last = step ?: last
            }
        }
    }
}
