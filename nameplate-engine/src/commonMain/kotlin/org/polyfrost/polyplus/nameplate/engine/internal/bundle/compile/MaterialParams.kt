package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.materialLayers
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.mirrorFrame
import org.polyfrost.polyplus.nameplate.engine.internal.anim.phaseOffset
import org.polyfrost.polyplus.nameplate.engine.internal.anim.states
import org.polyfrost.polyplus.nameplate.engine.internal.anim.triggered
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.MaterialLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Trigger
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.issue.keyValues
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialClock
import org.polyfrost.polyplus.nameplate.engine.internal.track.Interpolation
import org.polyfrost.polyplus.nameplate.engine.internal.track.MaterialParamTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.ParamChannels
import org.polyfrost.polyplus.nameplate.engine.internal.track.ParamTrack

internal object MaterialParamCompiler {
    private const val SHAPE = "[step, value] or [step, value, \"step\" | \"linear\" | \"ease\"], value a number or \"base\""

    fun compile(manifest: ManifestJson): MaterialParamTracks {
        val animations = manifest.animations
        val loop = animations[AnimationCompiler.LOOP]?.takeIf { it.schedule == null && it.trigger == null }?.let(::channelsOf).orEmpty()
        val shots = ArrayList<Map<String, ParamChannels>>()
        val whiles = ArrayList<Map<String, ParamChannels>>()
        for ((name, trigger) in TriggerCompiler.triggered(animations)) {
            val channels = channelsOf(animations.getValue(name))
            if (trigger is Trigger.While) whiles.add(channels) else shots.add(channels)
        }
        if (loop.isEmpty() && shots.all { it.isEmpty() } && whiles.all { it.isEmpty() }) return MaterialParamTracks.NONE
        return MaterialParamTracks(loop, shots, whiles)
    }

    private fun channelsOf(animation: AnimationJson): Map<String, ParamChannels> {
        val found = LinkedHashMap<String, ParamChannels>()
        for ((target, channels) in animation.tracks) {
            val names = ArrayList<String>()
            val tracks = ArrayList<ParamTrack>()
            for ((channel, element) in channels) {
                if (!MaterialParamTracks.isChannel(channel)) continue
                val track = parse(element, "") { } ?: continue
                names.add(MaterialParamTracks.paramOf(channel))
                tracks.add(track)
            }
            if (names.isNotEmpty()) {
                found[target] = ParamChannels(names.toTypedArray(), tracks.toTypedArray())
            }
        }
        return found
    }

    fun parse(element: JsonElement, path: String, report: IssueSink): ParamTrack? {
        if (element !is JsonArray) {
            report(path, "must be a list of $SHAPE keys")
            return null
        }
        val steps = IntArray(element.size)
        val values = DoubleArray(element.size)
        val bases = BooleanArray(element.size)
        val modes = IntArray(element.size)
        var valid = true
        for ((i, entry) in element.withIndex()) {
            val key = entry as? JsonArray
            val step = key?.getOrNull(0)?.let(ChannelKeys::wholeNumber)
            val value = key?.getOrNull(1) as? JsonPrimitive
            val mode = if (key != null && key.size == 3) {
                (key[2] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let(Interpolation::parse)
            } else {
                Interpolation.STEP
            }
            val base = value?.isString == true && value.content == MaterialParamTracks.BASE
            val number = value?.takeIf { !it.isString }?.doubleOrNull
            when {
                key == null || key.size !in 2..3 || step == null || mode == null || (!base && number == null) -> {
                    report("$path[$i]", "must be $SHAPE")
                    valid = false
                }
                number != null && !number.isFinite() -> {
                    report("$path[$i]", "must be a finite number")
                    valid = false
                }
                else -> {
                    steps[i] = step
                    values[i] = number ?: 0.0
                    bases[i] = base
                    modes[i] = mode
                }
            }
        }
        return if (valid) ParamTrack(steps, values, bases, modes) else null
    }
}

internal class MaterialParamValidator(
    private val manifest: ManifestJson,
    private val layers: Map<String, LayerFileJson>,
    private val report: IssueSink,
) {
    private val params: Set<String> = buildSet {
        addAll(manifest.params.keys)
        manifest.variants.values.forEach { addAll(it.params.keys) }
    }

    fun validate(path: String, target: String, channel: String, element: JsonElement, length: Int?, scheduled: Boolean) {
        val name = MaterialParamTracks.paramOf(channel)
        when {
            scheduled -> report(path, "param tracks work in the loop, reactions and one-shots, not in schedules")
            layers[target] !is MaterialLayerJson -> report(path, "param tracks need a material layer")
            name !in params -> report(path, "no param '$name' in the manifest or its variants")
            else -> {
                var valid = true
                val track = MaterialParamCompiler.parse(element, path, report.onIssue { valid = false })
                if (track != null && valid) {
                    checkSteps(path, element as JsonArray, length)
                }
            }
        }
    }

    private fun checkSteps(path: String, keys: JsonArray, length: Int?) {
        if (keys.isEmpty()) {
            report(path, "needs at least one key")
            return
        }
        var previous = -1
        for ((i, entry) in keys.withIndex()) {
            val step = checkNotNull(ChannelKeys.wholeNumber((entry as JsonArray)[0]))
            val keyPath = "$path[$i]"
            when {
                i == 0 && step != 0 ->
                    report.issue(keyPath, "the first key must be at step 0", IssueCode.KEY_START, keyValues(step, length))
                i > 0 && step <= previous ->
                    report.issue(keyPath, "keys must be in increasing step order", IssueCode.KEY_ORDER, keyValues(step, length))
                step < 0 || (length != null && step >= length) ->
                    report.issue(keyPath, "step must be inside the animation's length", IssueCode.KEY_RANGE, keyValues(step, length))
                step > BundleLimits.MAX_TRACK_STEP -> report(keyPath, "step must be at most ${BundleLimits.MAX_TRACK_STEP}")
            }
            previous = step
        }
    }
}

internal object MaterialParams {
    private const val MAX_SHOT_STATES = 100_000

    fun at(
        bundle: NameplateBundle,
        layer: NameplateBundle.Layer,
        variant: NameplateBundle.Variant,
        frame: Int,
        seed: Int,
        state: TagState,
        mirrored: Boolean,
    ): Map<String, Double> {
        val tracks = bundle.materialParams
        val names = tracks.namesOf(layer.id) ?: return variant.params
        val shifted = mirrorFrame(layer, frame, mirrored)
        val loopTime = MaterialClock.state(shifted, layer.step, bundle.loop) * layer.step
        val time = shifted - phaseOffset(bundle, seed)
        val values = LinkedHashMap(variant.params)
        for (name in names) {
            val base = variant.params[name] ?: 0.0
            values[name] = triggered(bundle, layer, name, base, state, time, loopTime)
                ?: tracks.loopOf(layer.id)?.track(name)?.at(loopTime, base)
                ?: base
        }
        return values
    }

    fun inShot(bundle: NameplateBundle, layer: NameplateBundle.Layer, frame: Int, seed: Int, state: TagState, mirrored: Boolean): Boolean {
        if (state.isIdle || state.isReduced || state.oneShotCount == 0) return false
        val tracks = bundle.materialParams
        val time = mirrorFrame(layer, frame, mirrored) - phaseOffset(bundle, seed)
        for (i in 0 until state.oneShotCount) {
            val index = state.oneShot(i)
            val shot = bundle.triggers.oneShots.getOrNull(index) ?: continue
            if (tracks.shotOf(index, layer.id) != null && time - state.start(i) in 0 until shot.length) return true
        }
        return false
    }

    fun values(bundle: NameplateBundle, layerId: String, params: Map<String, Double>): DoubleArray? {
        val names = bundle.materialParams.namesOf(layerId) ?: return null
        return DoubleArray(names.size) { params[names[it]] ?: 0.0 }
    }

    private fun triggered(
        bundle: NameplateBundle,
        layer: NameplateBundle.Layer,
        name: String,
        base: Double,
        state: TagState,
        time: Int,
        loopTime: Int,
    ): Double? {
        if (state.isIdle) return null
        val tracks = bundle.materialParams
        val triggers = bundle.triggers
        var best: Double? = null
        var bestStart = 0
        var bestIndex = 0
        val shots = if (state.isReduced) 0 else state.oneShotCount
        for (i in 0 until shots) {
            val index = state.oneShot(i)
            val shot = triggers.oneShots.getOrNull(index) ?: continue
            val track = tracks.shotOf(index, layer.id)?.track(name) ?: continue
            val start = state.start(i)
            val local = time - start
            val newer = best == null || start > bestStart || (start == bestStart && index < bestIndex)
            if (local in 0 until shot.length && newer) {
                best = track.at(local / layer.step * layer.step, base)
                bestStart = start
                bestIndex = index
            }
        }
        if (best != null || state.inputs.isEmpty) return best
        for ((index, reaction) in triggers.whiles.withIndex()) {
            if (!state.inputs.has(reaction.reaction)) continue
            if (state.isReduced && reaction.dropsWhenReduced) continue
            val track = tracks.whileOf(index, layer.id)?.track(name) ?: continue
            return if (state.isReduced) track.first(base) else track.at(loopTime, base)
        }
        return null
    }

    fun states(bundle: NameplateBundle, layer: NameplateBundle.Layer, content: MaterialLayer, limit: Int = Int.MAX_VALUE): Int {
        val tracks = bundle.materialParams
        val names = tracks.namesOf(layer.id)
        val cycle = bundle.loop / layer.step
        val animated = content.material.animated && !content.drift
        if (names == null) return if (animated) cycle else 1
        return bundle.variants.values.maxOf { variant -> StateCount(bundle, layer, variant, names, animated, limit, false).count() }
    }

    fun shotStates(bundle: NameplateBundle, layer: NameplateBundle.Layer, content: MaterialLayer): Int {
        val tracks = bundle.materialParams
        val names = tracks.namesOf(layer.id) ?: return 0
        if ((0 until tracks.shotCount).none { tracks.shotOf(it, layer.id) != null }) return 0
        val animated = content.material.animated && !content.drift
        return bundle.variants.values.maxOf { variant ->
            StateCount(bundle, layer, variant, names, animated, MAX_SHOT_STATES, true).count() -
                StateCount(bundle, layer, variant, names, animated, Int.MAX_VALUE, false).count()
        }.coerceAtLeast(0)
    }

    fun stateIssues(bundle: NameplateBundle, limit: Int = BundleLimits.MAX_CACHED_STATES): List<BundleIssue> =
        bundle.materialLayers.mapNotNull { (layer, content) ->
            if (bundle.materialParams.namesOf(layer.id) == null) return@mapNotNull null
            val count = states(bundle, layer, content, limit + 1)
            if (count > limit) {
                BundleIssue(
                    BundleFiles.layerPath(layer.id),
                    "shows more than $limit different images in its loop and while-reactions (frames times animated param values); " +
                        "at most $limit (raise its step, or use fewer keys on its loop and reaction params; one-shots don't count)",
                    IssueCode.OVER_STATES,
                    issueValues("limit" to limit),
                )
            } else {
                null
            }
        }

    private class StateCount(
        private val bundle: NameplateBundle,
        private val layer: NameplateBundle.Layer,
        private val variant: NameplateBundle.Variant,
        private val names: Array<String>,
        private val animated: Boolean,
        private val limit: Int,
        private val shots: Boolean,
    ) {
        private val tracks = bundle.materialParams
        private val cycle = bundle.loop / layer.step
        private val seen = HashSet<List<Any>>()

        fun count(): Int {
            for (m in 0 until cycle) {
                add(m, vector(m) { null })
                for (index in 0 until tracks.whileCount) {
                    val channels = tracks.whileOf(index, layer.id) ?: continue
                    add(m, vector(m) { name -> channels.track(name)?.at(m * layer.step, base(name)) })
                    if (!bundle.triggers.whiles[index].dropsWhenReduced) {
                        add(m, vector(m) { name -> channels.track(name)?.first(base(name)) })
                    }
                }
                for (index in 0 until if (shots) tracks.shotCount else 0) {
                    val channels = tracks.shotOf(index, layer.id) ?: continue
                    val length = bundle.triggers.oneShots[index].length
                    var local = 0
                    while (local < length) {
                        add(m, vector(m) { name -> channels.track(name)?.at(local, base(name)) })
                        local += layer.step
                    }
                }
                if (seen.size >= limit) break
            }
            return seen.size
        }

        private fun base(name: String): Double = variant.params[name] ?: 0.0

        private inline fun vector(multiple: Int, driven: (String) -> Double?): List<Double> = names.map { name ->
            driven(name) ?: tracks.loopOf(layer.id)?.track(name)?.at(multiple * layer.step, base(name)) ?: base(name)
        }

        private fun add(multiple: Int, vector: List<Double>) {
            seen.add(listOf(if (animated) multiple else 0) + vector)
        }
    }
}
