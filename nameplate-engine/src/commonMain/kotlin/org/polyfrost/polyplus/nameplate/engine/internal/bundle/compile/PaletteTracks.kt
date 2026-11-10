package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.polyfrost.polyplus.molang.MolangEnvironment
import org.polyfrost.polyplus.molang.MolangValue
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.internal.palette.PaletteTrack
import org.polyfrost.polyplus.nameplate.engine.internal.track.BundleMolang

internal object PaletteTracks {
    private const val LOOP = "loop"
    private const val PREFIX = "palette."
    private const val CYCLE = "cycle"
    private const val KEYS = "keys"
    private const val BASE = "base"
    private const val KEY_SHAPE = "must be [step, color] or [step, color, step | linear | ease], color a color or \"base\""
    private const val MANIFEST = BundleFiles.MANIFEST
    private const val OPAQUE_BLACK = 0xFF000000.toInt()

    private val MODES = mapOf(
        "forward" to PaletteTrack.CycleMode.FORWARD,
        "backward" to PaletteTrack.CycleMode.BACKWARD,
        "pingpong" to PaletteTrack.CycleMode.PINGPONG,
    )
    private val EASINGS = mapOf(
        "step" to PaletteTrack.Easing.STEP,
        "linear" to PaletteTrack.Easing.LINEAR,
        "ease" to PaletteTrack.Easing.EASE,
    )

    private class CycleSpec(val every: Int, val mode: PaletteTrack.CycleMode)

    private class Tone(val ref: ColorRef?, val molang: MolangValue?, val base: Boolean = false)

    private class KeysSpec(val steps: IntArray, val easings: Array<PaletteTrack.Easing>, val values: List<List<Tone>>)

    private class Spec(val slot: String, val cycle: CycleSpec?, val keys: KeysSpec?)

    private class Context(
        val loop: Int,
        val tones: Map<String, Int>,
        val environment: MolangEnvironment,
        val report: IssueSink,
    )

    fun isPaletteTrack(name: String): Boolean = name.startsWith(PREFIX)

    fun cyclePeriod(mode: PaletteTrack.CycleMode, tones: Int, every: Int): Long {
        if (tones <= 1) return 1L

        val rotations = if (mode == PaletteTrack.CycleMode.PINGPONG) 2L * (tones - 1) else tones.toLong()
        return rotations * every
    }

    fun validate(manifest: ManifestJson, tones: Map<String, Int>, report: IssueSink) {
        val tracks = loopTracks(manifest) ?: return
        val context = Context(manifest.loop, tones, BundleMolang.slotEnvironment(manifest), report)
        for ((name, channels) in tracks) {
            parse(name, channels, "$MANIFEST#animations.$LOOP.tracks.$name", context)
        }
    }

    fun load(manifest: ManifestJson, base: Palette, molang: BundleMolang): List<PaletteTrack> {
        val tracks = loopTracks(manifest) ?: return emptyList()
        val context = Context(manifest.loop, base.slots.associate { it.id to it.size }, molang.slotEnvironment) { }
        val loaded = ArrayList<PaletteTrack>()
        for ((name, channels) in tracks) {
            val spec = parse(name, channels, "", context) ?: continue
            val slot = base.indexOfSlot(spec.slot)
            spec.cycle?.let { loaded.add(PaletteTrack.Cycle(slot, it.every, it.mode)) }
            spec.keys?.let { loaded.add(keys(slot, it, base, molang)) }
        }

        return loaded
    }

    fun variantIssues(bundle: NameplateBundle): List<BundleIssue> {
        val issues = ArrayList<BundleIssue>()
        for (track in bundle.paletteTracks) {
            if (track !is PaletteTrack.Cycle) continue

            val id = bundle.palette.slots[track.slot].id
            for ((key, variant) in bundle.variants) {
                val tones = variant.palette.slots[track.slot].size
                if (bundle.loop % cyclePeriod(track.mode, tones, track.every) != 0L) {
                    issues.add(
                        BundleIssue(
                            "$MANIFEST#animations.$LOOP.tracks.$PREFIX$id.$CYCLE",
                            "variant '$key' has $tones tones, so the cycle's period must divide the loop (${bundle.loop})",
                            IssueCode.PALETTE_CYCLE,
                            issueValues("tones" to tones, "loop" to bundle.loop),
                        ),
                    )
                }
            }
        }

        return issues
    }

    private fun loopTracks(manifest: ManifestJson): Map<String, Map<String, JsonElement>>? {
        val loop = manifest.animations[LOOP] ?: return null
        if (loop.schedule != null || loop.trigger != null) return null

        return loop.tracks.filterKeys(::isPaletteTrack)
    }

    private fun keys(slot: Int, spec: KeysSpec, base: Palette, molang: BundleMolang): PaletteTrack.Keys {
        val tones = spec.values[0].size
        val colors = Array(spec.steps.size) { IntArray(tones) { OPAQUE_BLACK } }
        val refs = Array(spec.steps.size) { IntArray(tones) { -1 } }
        val expressions = Array(spec.steps.size) { IntArray(tones) { -1 } }
        val bases = Array(spec.steps.size) { BooleanArray(tones) }
        var usesMolang = false
        var usesBase = false
        for ((key, values) in spec.values.withIndex()) {
            for ((tone, value) in values.withIndex()) {
                val expression = value.molang
                if (value.base) {
                    bases[key][tone] = true
                    usesBase = true
                    continue
                }
                when (val ref = value.ref) {
                    is ColorRef.Hex -> colors[key][tone] = ref.argb
                    is ColorRef.Slot -> refs[key][tone] = base.ref(ref.id, ref.tone)
                    null -> if (expression != null) {
                        expressions[key][tone] = molang.addSlot(expression)
                        usesMolang = true
                    }
                    else -> error("'$ref' is not a color")
                }
            }
        }

        return PaletteTrack.Keys(slot, spec.steps, spec.easings, colors, refs, expressions.takeIf { usesMolang }, bases.takeIf { usesBase })
    }

    private fun parse(name: String, channels: Map<String, JsonElement>, path: String, context: Context): Spec? {
        val slot = name.removePrefix(PREFIX)
        val count = context.tones[slot]
        if (count == null) {
            context.report(path, "no slot '$slot'")
            return null
        }

        val unknown = channels.keys.filter { it != CYCLE && it != KEYS }
        for (channel in unknown) {
            context.report("$path.$channel", "must be $CYCLE or $KEYS")
        }
        when {
            CYCLE in channels && KEYS in channels -> context.report(path, "takes $CYCLE or $KEYS, not both")
            CYCLE !in channels && KEYS !in channels -> if (unknown.isEmpty()) context.report(path, "needs $CYCLE or $KEYS")
            else -> {
                val cycle = channels[CYCLE]?.let { parseCycle(it, "$path.$CYCLE", count, context) }
                val keys = channels[KEYS]?.let { parseKeys(it, "$path.$KEYS", count, context) }
                val failed = (CYCLE in channels && cycle == null) || (KEYS in channels && keys == null)
                return if (failed || unknown.isNotEmpty()) null else Spec(slot, cycle, keys)
            }
        }

        return null
    }

    private fun parseCycle(element: JsonElement, path: String, tones: Int, context: Context): CycleSpec? {
        if (element !is JsonObject) {
            context.report(path, "must be { \"every\": steps, \"mode\": forward | backward | pingpong }")
            return null
        }

        val every = whole(element["every"])
        val mode = (element["mode"] as? JsonPrimitive)?.takeIf { it.isString }?.let { MODES[it.content] }
        if (every == null || every < 1) {
            context.report("$path.every", "must be a whole number, 1 or more")
        }
        if (mode == null) {
            context.report("$path.mode", "must be one of ${MODES.keys.joinToString()}")
        }
        if (every == null || every < 1 || mode == null) return null

        val period = cyclePeriod(mode, tones, every)
        if (context.loop % period != 0L) {
            context.report.issue(
                path,
                "a cycle of $period frames must divide the loop (${context.loop})",
                IssueCode.PALETTE_CYCLE,
                issueValues("cycle" to period, "length" to context.loop),
            )
            return null
        }

        return CycleSpec(every, mode)
    }

    private fun parseKeys(element: JsonElement, path: String, tones: Int, context: Context): KeysSpec? {
        if (element !is JsonArray) {
            context.report(path, "must be a list of [step, color] keys")
            return null
        }
        if (element.isEmpty()) {
            context.report(path, "needs at least one key")
            return null
        }

        val steps = IntArray(element.size)
        val easings = Array(element.size) { PaletteTrack.Easing.STEP }
        val values = ArrayList<List<Tone>>()
        var valid = true
        for ((i, entry) in element.withIndex()) {
            val keyPath = "$path[$i]"
            val key = entry as? JsonArray
            val step = key?.getOrNull(0)?.let(::whole)
            if (key == null || key.size !in 2..3 || step == null) {
                context.report(keyPath, KEY_SHAPE)
                valid = false
                continue
            }

            steps[i] = step
            val previous = if (i > 0) steps[i - 1] else -1
            val order = when {
                i == 0 && step != 0 -> IssueCode.KEY_START to "the first key must be at step 0"
                i > 0 && step <= previous -> IssueCode.KEY_ORDER to "keys must be in increasing step order"
                step < 0 || step >= context.loop -> IssueCode.KEY_RANGE to "step must be inside the loop (0 to ${context.loop - 1})"
                else -> null
            }
            if (order != null) {
                context.report.issue(keyPath, order.second, order.first, issueValues("step" to step, "length" to context.loop))
                valid = false
            }

            val colors = colors(key[1], keyPath, tones, context)
            if (colors == null) {
                valid = false
            } else {
                values.add(colors)
            }
            if (key.size == 3) {
                val easing = (key[2] as? JsonPrimitive)?.takeIf { it.isString }?.let { EASINGS[it.content] }
                if (easing == null) {
                    context.report(keyPath, "interpolation must be one of ${EASINGS.keys.joinToString()}")
                    valid = false
                } else {
                    easings[i] = easing
                }
            }
        }

        return if (valid) KeysSpec(steps, easings, values) else null
    }

    private fun colors(value: JsonElement, path: String, tones: Int, context: Context): List<Tone>? {
        val texts = when {
            value is JsonPrimitive && value.isString && value.content == BASE -> List(tones) { BASE }
            value is JsonArray && value.size == tones -> value.map { text(it) }
            value is JsonPrimitive && value.isString && tones == 1 -> listOf(value.content)
            else -> {
                context.report(path, if (tones == 1) "needs one color or \"base\"" else "needs a list of $tones colors, or \"base\"")
                return null
            }
        }

        val refs = ArrayList<Tone>()
        for (text in texts) {
            if (text == BASE) {
                refs.add(Tone(null, null, base = true))
                continue
            }
            val ref = text?.let(ColorRef::parse)
            if (text != null && ref == null && !text.startsWith('#')) {
                val message = "'$text' is a constant Molang expression; write it as #hex"
                refs.add(Tone(null, ChannelKeys.expression(text, path, context.report, context.environment, message) ?: return null))
                continue
            }
            val problem = when {
                ref is ColorRef.Hex -> null
                ref is ColorRef.Slot -> slotProblem(ref, context.tones)
                else -> "'${text.orEmpty()}' must be #hex, slot, slot[tone] or base"
            }
            if (ref == null || problem != null) {
                context.report(path, problem ?: "must be #hex, slot, slot[tone] or base")
                return null
            }
            refs.add(Tone(ref, null))
        }

        return refs
    }

    private fun slotProblem(ref: ColorRef.Slot, tones: Map<String, Int>): String? {
        val count = tones[ref.id]
        return when {
            count == null -> "no slot '${ref.id}'"
            ref.tone >= count -> "slot '${ref.id}' has no tone ${ref.tone}"
            else -> null
        }
    }

    private fun text(element: JsonElement): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun whole(element: JsonElement?): Int? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
}
