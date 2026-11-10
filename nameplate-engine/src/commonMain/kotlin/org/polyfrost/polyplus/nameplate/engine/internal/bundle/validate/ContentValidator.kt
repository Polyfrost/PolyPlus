package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import org.polyfrost.polyplus.nameplate.engine.api.bundle.Layout
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.anim.LayerMasks
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.AnimationCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.BodyCorners
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.ChannelKeys
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.MaterialLayers
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.PaletteTracks
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.EmitterLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.FillJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.FlagsJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.MaterialLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.OverlayJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.BodyArt
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.layout.Anchor
import org.polyfrost.polyplus.nameplate.engine.internal.layout.OrnamentAnchor
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef

internal object ContentValidator {
    private const val FLAGS = BundleFiles.FLAGS

    private const val ROWS = "rows"
    private const val COLUMNS = "columns"

    private val OVERLAYS = setOf("chevron", "triangle", "circle")

    fun usesFlags(manifest: ManifestJson, layers: Map<String, LayerFileJson>): Boolean =
        manifest.variants.values.any { it.flag != null } ||
            layers.values.filterIsInstance<BodyLayerJson>().any { body -> (listOfNotNull(body.fill) + body.rings).any { it.flag } }

    fun validate(
        manifest: ManifestJson,
        layers: Map<String, LayerFileJson>,
        sprites: Map<String, SpriteFileJson>,
        tones: Map<String, Int>,
        flags: FlagsJson? = null,
    ): List<BundleIssue> {
        val issues = ArrayList<BundleIssue>()
        val report = issueSink(issues)
        val steps = manifest.layers.associate { it.id to it.step }
        for ((id, layer) in layers) {
            when (layer) {
                is BodyLayerJson -> {
                    checkBody(BundleFiles.layerPath(id), layer, tones, flags, report)
                    layer.art?.let { BodyArt.validate("${BundleFiles.layerPath(id)}#art", it, sprites, report.coded(IssueCode.BODY_ART)) }
                    LayerTargets.bodyOf(BundleFiles.layerPath(id), layer, report)
                }
                is SpriteLayerJson -> checkSpriteLayer(BundleFiles.layerPath(id), layer, layers, sprites, report)
                is EmitterLayerJson -> Unit
                is MaterialLayerJson -> MaterialLayers.validate(BundleFiles.layerPath(id), layer, layers, manifest, report)
            }
        }
        LayerTargets.badges(manifest, layers, report)
        val keyed = framesKeyed(manifest)
        for ((id, sprite) in sprites) {
            val users = layers.filter { (layerId, file) ->
                (file as? SpriteLayerJson)?.let { layer ->
                    layer.sprite == id && layer.timeline == null && layer.path?.frames == null && layerId !in keyed
                } == true
            }.keys
            checkSprite(BundleFiles.spritePath(id), sprite, tones, users.map { manifest.loop / (steps[it] ?: 1) }, report)
        }
        if (flags != null) {
            checkFlags(flags, tones, report)
        }
        MaskValidator.validate(manifest, layers, report.coded(IssueCode.MASK))
        if (Layout.CAPABILITY !in manifest.requires && Layout.uses(manifest, layers)) {
            report.issue(
                "${BundleFiles.MANIFEST}#requires",
                "masks, body art, name anchors, at and edge following need \"${Layout.CAPABILITY}\" in requires",
                IssueCode.LAYOUT,
            )
        }
        AnimationValidator(manifest, layers, sprites, report, tones).validate()
        AdaptiveValidator(manifest, layers, sprites, report).validate()
        PaletteTracks.validate(manifest, tones, report)
        val flagIds = flags?.flags?.mapTo(HashSet()) { it.id }.orEmpty()
        for ((key, variant) in manifest.variants) {
            val flag = variant.flag
            if (flag != null && flag !in flagIds) {
                report("${BundleFiles.MANIFEST}#variants.$key.flag", "no flag '$flag'")
            }
        }
        return issues
    }

    private fun checkFlags(flags: FlagsJson, tones: Map<String, Int>, report: IssueSink) {
        if (flags.flags.isEmpty() || flags.flags.size > BundleLimits.MAX_FLAGS) {
            report("$FLAGS#flags", "must hold 1 to ${BundleLimits.MAX_FLAGS} flags")
        }
        val seen = HashSet<String>()
        for (flag in flags.flags) {
            val path = "$FLAGS#flags.${flag.id}"
            if (!BundleFiles.isSpriteId(flag.id)) {
                report(path, "id must be 1 to 64 of a-z, 0-9 and _")
            }
            if (!seen.add(flag.id)) {
                report(path, "duplicate flag")
            }
            if (flag.bands.isEmpty() || flag.bands.size > BundleLimits.MAX_BANDS) {
                report("$path.bands", "must hold 1 to ${BundleLimits.MAX_BANDS} bands")
            }
            for ((i, band) in flag.bands.withIndex()) {
                checkSlotRef(band.color, "$path.bands[$i]", tones, report)
                if (band.weight !in 1..BundleLimits.MAX_BAND_WEIGHT) {
                    report("$path.bands[$i]", "weight must be 1 to ${BundleLimits.MAX_BAND_WEIGHT}")
                }
            }
            flag.overlay?.let { checkOverlay("$path.overlay", it, tones, report) }
            if (flag.axis != ROWS && flag.axis != COLUMNS) {
                report("$path.axis", "must be $ROWS or $COLUMNS")
            }
            if (flag.axis == COLUMNS && flag.rowsOverride.isNotEmpty()) {
                report("$path.rowsOverride", "only works on a flag with rows; $COLUMNS are fitted to the body's width")
            }
            val weights = flag.bands.map { it.weight }
            if (flag.symmetric && weights != weights.reversed()) {
                report("$path.symmetric", "needs band weights that read the same both ways")
            }
            for ((rows, fitted) in flag.rowsOverride) {
                val count = rows.toIntOrNull()
                val valid = count != null && fitted.size == flag.bands.size && fitted.all { it >= 1 } && fitted.sum() == count
                if (!valid) {
                    report("$path.rowsOverride.$rows", "must give each of the ${flag.bands.size} bands 1 or more rows, summing to $rows")
                }
            }
        }
    }

    private fun checkOverlay(path: String, overlay: OverlayJson, tones: Map<String, Int>, report: IssueSink) {
        if (overlay.kind !in OVERLAYS) {
            report("$path.kind", "must be one of ${OVERLAYS.joinToString()}")
        }
        val allowed = when (overlay.kind) {
            "triangle" -> 1..1
            "circle" -> 1..2
            else -> 1..BundleLimits.MAX_OVERLAY_COLORS
        }
        if (overlay.colors.size !in allowed) {
            report("$path.colors", "a ${overlay.kind} takes ${allowed.first} to ${allowed.last} colors")
        }
        for ((i, color) in overlay.colors.withIndex()) {
            checkSlotRef(color, "$path.colors[$i]", tones, report)
        }
        if (overlay.width !in 1..BundleLimits.MAX_OVERLAY_WIDTH) {
            report("$path.width", "must be 1 to ${BundleLimits.MAX_OVERLAY_WIDTH}")
        }
    }

    private fun checkBody(
        path: String,
        body: BodyLayerJson,
        tones: Map<String, Int>,
        flags: FlagsJson?,
        report: IssueSink,
    ) {
        val usesFlag = (listOfNotNull(body.fill) + body.rings).any { it.flag }
        if (usesFlag && body.padding.size == 4) {
            val rows = PixelRect.GLYPH_HEIGHT + body.padding[1] + body.padding[3]
            val all = flags?.flags.orEmpty()
            val maxBands = all.filter { it.axis != COLUMNS }.maxOfOrNull { it.bands.size } ?: 0
            if (all.all { it.bands.isEmpty() }) {
                report("$path#fill", "a flag fill needs $FLAGS")
            } else if (maxBands > rows) {
                report("$path#fill", "a flag of $maxBands bands needs $maxBands rows; a one-line body has $rows")
            }
        }
        if (body.padding.size != 4 || body.padding.any { it !in 0..BundleLimits.MAX_PADDING }) {
            report("$path#padding", "must be [left, top, right, bottom], each 0 to ${BundleLimits.MAX_PADDING}")
        }
        if (body.minWidth !in 0..BundleLimits.MAX_MIN_WIDTH) {
            report("$path#minWidth", "must be 0 to ${BundleLimits.MAX_MIN_WIDTH}")
        }
        if (body.radius !in 0..BundleLimits.MAX_RADIUS) {
            report.issue(
                "$path#radius",
                "must be 0 to ${BundleLimits.MAX_RADIUS}",
                IssueCode.CORNERS,
                issueValues("limit" to BundleLimits.MAX_RADIUS),
            )
        }
        BodyCorners.validate(path, body, report.coded(IssueCode.CORNERS))
        if (body.rings.size > BundleLimits.MAX_RINGS) {
            report("$path#rings", "at most ${BundleLimits.MAX_RINGS} rings")
        }
        body.fill?.let { checkFill("$path#fill", it, tones, report) }
        for ((i, ring) in body.rings.withIndex()) {
            checkFill("$path#rings[$i]", ring, tones, report)
        }
    }

    private fun checkFill(path: String, fill: FillJson, tones: Map<String, Int>, report: IssueSink) {
        val color = fill.color
        val gradient = fill.gradient
        val blend = fill.blend
        val sweep = fill.sweep
        if (fill.axis != null && sweep == null) {
            report(path, "axis is only used with sweep")
        }
        when {
            listOf(color != null, gradient != null, blend != null, fill.flag, sweep != null).count { it } != 1 ->
                report(path, "needs exactly one of color, gradient, blend, flag or sweep")
            color != null -> checkSlotRef(color, path, tones, report)
            gradient != null && gradient !in tones -> report(path, "no slot '$gradient'")
            blend != null && blend !in tones -> report(path, "no slot '$blend'")
            sweep != null && sweep !in tones -> report(path, "no slot '$sweep'")
            sweep != null && fill.axis != "x" && fill.axis != "y" -> report(path, "a sweep needs axis x or y")
        }
    }

    private fun checkSpriteLayer(
        path: String,
        layer: SpriteLayerJson,
        layers: Map<String, LayerFileJson>,
        sprites: Map<String, SpriteFileJson>,
        report: IssueSink,
    ) {
        if (layer.sprite !in sprites) {
            report("$path#sprite", "no sprite '${layer.sprite}'")
        }
        LayerTargets.spriteOf(path, layer, layers, report)
        LayerTargets.spriteFollow(path, layer, report)
        checkAnchor(path, layer, report)
        val pivot = layer.pivot
        if (pivot != null && Anchor.named(pivot) == null) {
            report("$path#pivot", "must be one of ${BundleValidator.ANCHORS.joinToString()}")
        }
        if (layer.offset.size != 2 || layer.offset.any { it !in -BundleLimits.MAX_OFFSET..BundleLimits.MAX_OFFSET }) {
            report("$path#offset", "must be [x, y], each within ${BundleLimits.MAX_OFFSET} px")
        }
    }

    private fun checkAnchor(path: String, layer: SpriteLayerJson, sink: IssueSink) {
        val report = sink.coded(IssueCode.ANCHOR)
        val at = layer.at
        when {
            layer.anchor !in OrnamentAnchor.NAMES ->
                report("$path#anchor", "must be one of ${OrnamentAnchor.NAMES.joinToString()}")
            OrnamentAnchor.isName(layer.anchor) && layer.of != null ->
                report("$path#of", "a name anchor sits on the name, not on a body; leave of out")
            at != null && !OrnamentAnchor.takesAt(layer.anchor) ->
                report("$path#at", "only top, bottom, left and right take at")
            at != null && (!at.isFinite() || at < 0.0 || at > 1.0) ->
                report("$path#at", "must be 0 to 1")
        }
    }

    private fun framesKeyed(manifest: ManifestJson): Set<String> {
        val loop = manifest.animations[AnimationCompiler.LOOP]?.takeIf { it.schedule == null && it.trigger == null } ?: return emptySet()
        return loop.tracks.filterValues { ChannelKeys.FRAME in it }.keys
    }

    private fun checkSprite(
        path: String,
        sprite: SpriteFileJson,
        tones: Map<String, Int>,
        cycles: List<Int>,
        report: IssueSink,
    ) {
        if (sprite.colors.size > BundleLimits.MAX_SPRITE_COLORS) {
            report("$path#colors", "at most ${BundleLimits.MAX_SPRITE_COLORS} colors")
        }
        for ((key, ref) in sprite.colors) {
            if (key.length != 1 || key == "." || key[0].isWhitespace()) {
                report("$path#colors.$key", "keys must be one character other than '.'")
            }
            checkSlotRef(ref, "$path#colors.$key", tones, report)
        }

        val frames = BundleFiles.framesOf(sprite)
        if (frames == null) {
            report(path, "needs exactly one of rows or frames")
            return
        }
        if (frames.isEmpty() || frames.size > BundleLimits.MAX_SPRITE_FRAMES) {
            report("$path#frames", "must hold 1 to ${BundleLimits.MAX_SPRITE_FRAMES} frames")
            return
        }

        val height = frames[0].size
        val width = frames[0].firstOrNull()?.length ?: 0
        if (width !in 1..BundleLimits.MAX_SPRITE_SIZE || height !in 1..BundleLimits.MAX_SPRITE_SIZE) {
            report(path, "frames must be 1 to ${BundleLimits.MAX_SPRITE_SIZE} px each way")
        }
        for ((f, rows) in frames.withIndex()) {
            if (rows.size != height || rows.any { it.length != width }) {
                report("$path#frames[$f]", "every frame and row must be $width x $height")
            }
            val unknown = rows.flatMap { it.toList() }.filter { it != '.' && it.toString() !in sprite.colors }.toSet()
            if (unknown.isNotEmpty()) {
                report("$path#frames[$f]", "uses ${unknown.joinToString { "'$it'" }}, not in colors")
            }
        }
        for (cycle in cycles.toSet()) {
            if (cycle % frames.size != 0) {
                report.issue(
                    "$path#frames",
                    "${frames.size} frames don't divide the layer's $cycle-frame cycle",
                    IssueCode.SPRITE_FRAMES,
                    issueValues("frames" to frames.size, "cycle" to cycle),
                )
            }
        }
    }

    fun cachedStateIssues(bundle: NameplateBundle, limit: Int = BundleLimits.MAX_CACHED_STATES): List<BundleIssue> =
        bundle.layers.mapNotNull { layer ->
            val count = LayerMasks.cachedStates(bundle, layer)
            if (count > limit) {
                BundleIssue(
                    BundleFiles.layerPath(layer.id),
                    "shows $count different frames; at most $limit",
                    IssueCode.OVER_STATES,
                    issueValues("frames" to count, "limit" to limit),
                )
            } else {
                null
            }
        }

    private fun checkSlotRef(value: String, path: String, tones: Map<String, Int>, report: IssueSink) {
        when (val ref = ColorRef.parse(value)) {
            is ColorRef.Slot -> {
                val count = tones[ref.id]
                when {
                    count == null -> report(path, "no slot '${ref.id}'")
                    ref.tone >= count -> report(path, "slot '${ref.id}' has no tone ${ref.tone}")
                }
            }
            else -> report(path, "must be a slot or slot[tone]")
        }
    }
}
