package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.VariantOverrides
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BadgeJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.PalettesJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SlotJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.TextJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.VariantJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.layout.OrnamentAnchor
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef
import org.polyfrost.polyplus.nameplate.engine.internal.palette.HexColor

internal object BundleValidator {
    private const val MANIFEST = BundleFiles.MANIFEST
    private const val PALETTES = BundleFiles.PALETTES
    const val DEFAULT_COLORWAY = "default"

    val TEXT_MODES: Set<String> = setOf("vanilla", "motion", "owned")
    val FORMATTINGS: Set<String> = setOf("keep", "override", "adjust")
    val DEPTHS: Set<String> = setOf("behind", "background", "over_background", "over_text")
    val PLACEMENTS: Set<String> = setOf("reserve", "carried", "placed")
    val ANCHORS: Set<String> =
        setOf("top_left", "top", "top_right", "left", "right", "bottom_left", "bottom", "bottom_right")
    val DECORATIONS: Set<String> = setOf("none", "auto", "shadow", "outline")

    private val ID = Regex("[a-z0-9_]{1,64}")
    private val RESERVED_SLOTS = setOf("auto", "none")
    private const val ID_RULE = "must be 1 to 64 of a-z, 0-9 and _"

    fun colorways(palettes: PalettesJson): Map<String, Map<String, List<String>>> =
        palettes.colorways.ifEmpty { mapOf(DEFAULT_COLORWAY to emptyMap()) }

    fun tones(slot: SlotJson): Int = slot.interpolate ?: slot.ramp.size

    fun validate(manifest: ManifestJson, palettes: PalettesJson): List<BundleIssue> {
        val issues = ArrayList<BundleIssue>()
        val sink = issueSink(issues)
        val report = object : Report {
            override fun issue(path: String, message: String) = sink(path, message)

            override fun issue(path: String, message: String, values: Map<String, Double>) =
                sink.issue(path, message, IssueCode.of(path), values)
        }
        checkManifest(manifest, report)
        checkLayers(manifest, report)
        val slots = checkPalettes(palettes, report)
        checkVariants(manifest, colorways(palettes).keys, slots, report)
        checkText(manifest.text, "$MANIFEST#text", slots, report)
        TextProgramValidator.validate(manifest, sink.coded(IssueCode.LETTER_PROGRAM))
        checkBadge(manifest.badge, "$MANIFEST#badge", slots, report)
        return issues
    }

    private fun interface Report {
        fun issue(path: String, message: String)

        fun issue(path: String, message: String, values: Map<String, Double>) = issue(path, message)
    }

    private fun checkManifest(manifest: ManifestJson, report: Report) {
        if (!ID.matches(manifest.id)) {
            report.issue("$MANIFEST#id", ID_RULE)
        }
        if (manifest.fps !in BundleLimits.MIN_FPS..BundleLimits.MAX_FPS) {
            report.issue(
                "$MANIFEST#fps",
                "must be ${BundleLimits.MIN_FPS} to ${BundleLimits.MAX_FPS}",
                issueValues("value" to manifest.fps, "min" to BundleLimits.MIN_FPS, "max" to BundleLimits.MAX_FPS),
            )
        }
        if (manifest.loop !in 1..BundleLimits.MAX_LOOP) {
            report.issue(
                "$MANIFEST#loop",
                "must be 1 to ${BundleLimits.MAX_LOOP} frames",
                issueValues("value" to manifest.loop, "max" to BundleLimits.MAX_LOOP),
            )
        }
        if (manifest.params.size > BundleLimits.MAX_PARAMS) {
            report.issue(
                "$MANIFEST#params",
                "at most ${BundleLimits.MAX_PARAMS} params",
                issueValues("count" to manifest.params.size, "limit" to BundleLimits.MAX_PARAMS),
            )
        }
        checkParamValues(manifest.params, "$MANIFEST#params", report)
    }

    private fun checkParamValues(params: Map<String, Double>, path: String, report: Report) {
        for ((name, value) in params) {
            if (!ID.matches(name)) {
                report.issue("$path.$name", "name $ID_RULE")
            }
            if (!value.isFinite()) {
                report.issue("$path.$name", "must be a finite number")
            }
        }
    }

    private fun checkLayers(manifest: ManifestJson, report: Report) {
        if (manifest.layers.size > BundleLimits.MAX_LAYERS) {
            report.issue(
                "$MANIFEST#layers",
                "at most ${BundleLimits.MAX_LAYERS} layers",
                issueValues("count" to manifest.layers.size, "limit" to BundleLimits.MAX_LAYERS),
            )
        }

        val ids = manifest.layers.mapTo(HashSet()) { it.id }
        val seen = HashSet<String>()
        for (layer in manifest.layers) {
            val path = "$MANIFEST#layers.${layer.id}"
            if (!ID.matches(layer.id)) {
                report.issue(path, "id $ID_RULE")
            }
            if (!seen.add(layer.id)) {
                report.issue(path, "duplicate layer")
            }
            if (layer.depth !in DEPTHS) {
                report.issue("$path.depth", "must be one of ${DEPTHS.joinToString()}")
            }
            if (layer.step < 1 || manifest.loop % layer.step != 0) {
                report.issue("$path.step", "must be at least 1 and divide the loop (${manifest.loop})")
            }
            val mask = layer.mask
            if (mask != null && mask != NAME_TARGET && (mask == layer.id || mask !in ids)) {
                report.issue("$path.mask", "must name another layer, or be $NAME_TARGET")
            }
        }
    }

    private fun checkPalettes(palettes: PalettesJson, report: Report): Map<String, Int> {
        val slots = LinkedHashMap<String, Int>()
        if (palettes.slots.isEmpty() || palettes.slots.size > BundleLimits.MAX_SLOTS) {
            report.issue(
                "$PALETTES#slots",
                "must hold 1 to ${BundleLimits.MAX_SLOTS} slots",
                issueValues("count" to palettes.slots.size, "limit" to BundleLimits.MAX_SLOTS),
            )
        }
        for (slot in palettes.slots) {
            val path = "$PALETTES#slots.${slot.id}"
            when {
                slot.id in RESERVED_SLOTS -> report.issue(path, "'${slot.id}' is reserved")
                !ID.matches(slot.id) -> report.issue(path, "id $ID_RULE")
                slot.id in slots -> report.issue(path, "duplicate slot")
            }
            checkRamp(slot.ramp, "$path.ramp", report)
            val interpolate = slot.interpolate
            if (interpolate != null &&
                (slot.ramp.size < 2 || interpolate < slot.ramp.size || interpolate > BundleLimits.MAX_TONES)
            ) {
                report.issue(
                    "$path.interpolate",
                    "needs 2 or more stops and must be from the stop count to ${BundleLimits.MAX_TONES}",
                )
            }
            slots.getOrPut(slot.id) { tones(slot) }
        }

        for ((name, overrides) in palettes.colorways) {
            val path = "$PALETTES#colorways.$name"
            if (!ID.matches(name)) {
                report.issue(path, "name $ID_RULE")
            }
            for ((slot, ramp) in overrides) {
                if (slot !in slots) {
                    report.issue("$path.$slot", "no such slot")
                }
                checkRamp(ramp, "$path.$slot", report)
            }
        }

        return slots
    }

    private fun checkRamp(ramp: List<String>, path: String, report: Report) {
        if (ramp.isEmpty() || ramp.size > BundleLimits.MAX_TONES) {
            report.issue(
                path,
                "must hold 1 to ${BundleLimits.MAX_TONES} tones",
                issueValues("count" to ramp.size, "limit" to BundleLimits.MAX_TONES),
            )
        }
        for ((i, color) in ramp.withIndex()) {
            if (HexColor.parse(color) == null) {
                report.issue("$path[$i]", "'$color' is not #RRGGBB or #AARRGGBB")
            }
        }
    }

    private fun checkVariants(manifest: ManifestJson, colorways: Set<String>, slots: Map<String, Int>, report: Report) {
        val layers = manifest.layers.mapTo(HashSet()) { it.id }
        for ((key, variant) in manifest.variants) {
            val path = "$MANIFEST#variants.$key"
            if (!ID.matches(key)) {
                report.issue(path, "key $ID_RULE")
            }
            if (variant.palette !in colorways) {
                report.issue("$path.palette", "no colorway '${variant.palette}'")
            }
            for (layer in variant.hide) {
                if (layer !in layers) {
                    report.issue("$path.hide", "no layer '$layer'")
                }
            }
            for ((from, to) in variant.swap) {
                if (from !in layers) {
                    report.issue("$path.swap", "no layer '$from'")
                }
                if (to !in layers) {
                    report.issue("$path.swap", "no layer '$to'")
                }
            }
            checkVariantOverrides(manifest, variant, path, slots, report)
        }
    }

    private fun checkVariantOverrides(
        manifest: ManifestJson,
        variant: VariantJson,
        path: String,
        slots: Map<String, Int>,
        report: Report,
    ) {
        checkParamValues(variant.params, "$path.params", report)
        if (VariantOverrides.params(manifest, variant).size > BundleLimits.MAX_PARAMS) {
            report.issue(
                "$path.params",
                "at most ${BundleLimits.MAX_PARAMS} params",
                issueValues("count" to VariantOverrides.params(manifest, variant).size, "limit" to BundleLimits.MAX_PARAMS),
            )
        }
        if (variant.text != null) {
            checkOverride(
                "$path.text",
                manifest.text,
                report,
                { VariantOverrides.text(manifest.text, variant.text) },
            ) { text, prefix, scoped -> checkText(text, prefix, slots, scoped) }
        }
        if (variant.badge != null) {
            checkOverride(
                "$path.badge",
                manifest.badge,
                report,
                { VariantOverrides.badge(manifest.badge, variant.badge) },
            ) { badge, prefix, scoped -> checkBadge(badge, prefix, slots, scoped) }
        }
    }

    private fun <T> checkOverride(path: String, base: T, report: Report, merge: () -> T, check: (T, String, Report) -> Unit) {
        val merged = try {
            merge()
        } catch (e: IllegalArgumentException) {
            report.issue(path, e.message ?: "does not match the format")
            return
        }

        val known = HashSet<Pair<String, String>>()
        check(base, "") { field, message -> known.add(field to message) }
        check(merged, "") { field, message ->
            if ((field to message) !in known) {
                report.issue(path + field, message)
            }
        }
    }

    private fun checkText(text: TextJson, path: String, slots: Map<String, Int>, report: Report) {
        if (text.mode !in TEXT_MODES) {
            report.issue("$path.mode", "must be one of ${TEXT_MODES.joinToString()}")
        }
        if (text.formatting !in FORMATTINGS) {
            report.issue("$path.formatting", "must be one of ${FORMATTINGS.joinToString()}")
        }
        checkRef(text.color, "$path.color", slots, report, allowKeywords = true)
        checkRef(text.shadow, "$path.shadow", slots, report, allowKeywords = true)
        checkRef(text.outline, "$path.outline", slots, report, allowKeywords = true)
        checkRefs(text.candidates, "$path.candidates", slots, report)
        for ((i, ref) in text.backdropHint.withIndex()) {
            checkRef(ref, "$path.backdropHint[$i]", slots, report, allowKeywords = false)
        }
    }

    private fun checkBadge(badge: BadgeJson, path: String, slots: Map<String, Int>, report: Report) {
        if (badge.placement !in PLACEMENTS) {
            report.issue("$path.placement", "must be one of ${PLACEMENTS.joinToString()}")
        }
        val anchor = badge.anchor
        when {
            badge.placement == "placed" && anchor == null -> report.issue("$path.anchor", "a placed badge needs one")
            anchor != null && anchor !in ANCHORS -> report.issue("$path.anchor", "must be one of ${ANCHORS.joinToString()}")
        }
        if (badge.offset.size != 2 || badge.offset.any { it !in -BundleLimits.MAX_BADGE_OFFSET..BundleLimits.MAX_BADGE_OFFSET }) {
            report.issue("$path.offset", "must be [x, y], each within ${BundleLimits.MAX_BADGE_OFFSET} px")
        }
        if (badge.tint != "none") {
            checkRef(badge.tint, "$path.tint", slots, report, allowKeywords = false)
        }
        if (badge.decoration !in DECORATIONS) {
            report.issue("$path.decoration", "must be one of ${DECORATIONS.joinToString()}")
        }
        val color = badge.decorationColor
        when {
            badge.decoration == "shadow" || badge.decoration == "outline" ->
                if (color == null) {
                    report.issue("$path.decorationColor", "needed for a ${badge.decoration}")
                } else {
                    checkRef(color, "$path.decorationColor", slots, report, allowKeywords = false)
                }
            color != null -> report.issue("$path.decorationColor", "only used with shadow or outline")
        }
        checkRefs(badge.candidates, "$path.candidates", slots, report)
        checkBadgeEdge(badge, path, report)
    }

    private fun checkBadgeEdge(badge: BadgeJson, path: String, report: Report) {
        val follow = badge.follow
        when {
            follow == null -> if (badge.of != null) report.issue("$path.of", "only used with follow")
            follow !in LayerTargets.FOLLOWS -> report.issue("$path.follow", "must be one of ${LayerTargets.FOLLOWS.joinToString()}")
            badge.placement != "placed" -> report.issue("$path.follow", "only a placed badge follows a body's edge")
            badge.of == null -> report.issue("$path.of", "a badge that follows an edge needs a body layer")
        }
        val at = badge.at ?: return
        when {
            follow == null -> report.issue("$path.at", "only used with follow")
            !OrnamentAnchor.takesAt(badge.anchor.orEmpty()) -> report.issue("$path.at", "only top, bottom, left and right take at")
            !at.isFinite() || at < 0.0 || at > 1.0 -> report.issue("$path.at", "must be 0 to 1")
        }
    }

    private fun checkRefs(refs: List<String>?, path: String, slots: Map<String, Int>, report: Report) {
        if (refs == null) return

        if (refs.isEmpty()) {
            report.issue(path, "needs at least one color")
        }
        for ((i, ref) in refs.withIndex()) {
            checkRef(ref, "$path[$i]", slots, report, allowKeywords = false)
        }
    }

    private fun checkRef(value: String, path: String, slots: Map<String, Int>, report: Report, allowKeywords: Boolean) {
        when (val ref = ColorRef.parse(value)) {
            null ->
                report.issue(
                    path,
                    if (allowKeywords) "must be auto, none, #hex, slot or slot[tone]" else "must be #hex, slot or slot[tone]",
                )
            ColorRef.Auto, ColorRef.None -> if (!allowKeywords) report.issue(path, "must be #hex, slot or slot[tone]")
            is ColorRef.Hex -> Unit
            is ColorRef.Slot -> {
                val tones = slots[ref.id]
                when {
                    tones == null -> report.issue(path, "no slot '${ref.id}'")
                    ref.tone >= tones -> report.issue(path, "slot '${ref.id}' has no tone ${ref.tone}")
                }
            }
        }
    }
}
