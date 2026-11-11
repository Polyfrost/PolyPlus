package org.polyfrost.polyplus.nameplate.engine.api.load

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleBytes
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.api.text.TextPaint
import org.polyfrost.polyplus.nameplate.engine.api.text.TextStyling
import org.polyfrost.polyplus.nameplate.engine.internal.anim.EdgeBadgeAnchor
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget.CostBudget
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget.MaterialFallbacks
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.AdaptiveCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.AnimationCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.BodyCorners
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.MaterialLayers
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.MaterialParamCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.MaterialParams
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.PaletteTracks
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.TriggerCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.VariantOverrides
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BadgeJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.EmitterLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.FillJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.FlagJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.FlagsJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.MaterialLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.PalettesJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.TextJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.VariantJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.BodyArt
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeFollow
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Fill
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Flag
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Overlay
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.OverlayKind
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PLATE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SweepAxis
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate.BundleValidator
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate.ContentValidator
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.layout.Anchor
import org.polyfrost.polyplus.nameplate.engine.internal.layout.BlockAnchor
import org.polyfrost.polyplus.nameplate.engine.internal.layout.OrnamentAnchor
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef
import org.polyfrost.polyplus.nameplate.engine.internal.palette.HexColor
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.internal.palette.RampInterpolation
import org.polyfrost.polyplus.nameplate.engine.internal.track.BundleMolang

class BundleLoader(private val capabilities: Set<String>) {
    fun load(source: BundleSource, bytes: BundleBytes = BundleBytes.NONE, budgets: Boolean = true): BundleLoad {
        val failures = ArrayList<BundleIssue>()
        val manifestElement = readJson(source, BundleFiles.MANIFEST, failures) ?: return BundleLoad.Invalid(failures)

        val format = (manifestElement as? JsonObject)?.get("format")?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
        when {
            format == null -> return invalid(BundleFiles.MANIFEST + "#format", "must be a whole number")
            format > BundleLimits.FORMAT -> return BundleLoad.Skipped("format $format is newer than ${BundleLimits.FORMAT}")
            format < 1 -> return invalid(BundleFiles.MANIFEST + "#format", "must be 1 or more")
        }

        val manifest = decode(manifestElement, ManifestJson.serializer(), BundleFiles.MANIFEST, JSON, failures)
            ?: return BundleLoad.Invalid(failures)

        val missing = manifest.requires.filter { it !in capabilities }
        if (missing.isNotEmpty()) return BundleLoad.Skipped("needs ${missing.joinToString()}")

        val palettes = readAndDecode(source, BundleFiles.PALETTES, PalettesJson.serializer(), failures, JSON)
            ?: return BundleLoad.Invalid(failures)

        val issues = BundleValidator.validate(manifest, palettes)
        if (issues.isNotEmpty()) return BundleLoad.Invalid(issues)

        val contentIssues = ArrayList<BundleIssue>()
        val layers = LinkedHashMap<String, LayerFileJson>()
        for (layer in manifest.layers) {
            val path = BundleFiles.layerPath(layer.id)
            readAndDecode(source, path, LayerFileJson.serializer(), contentIssues)?.let { layers[layer.id] = it }
        }
        val sprites = LinkedHashMap<String, SpriteFileJson>()
        for (id in layers.values.flatMap(AdaptiveCompiler::spriteIds).distinct()) {
            if (!BundleFiles.isSpriteId(id)) {
                contentIssues.add(BundleIssue("layers", "sprite id '$id' must be 1 to 64 of a-z, 0-9 and _"))
                continue
            }
            readAndDecode(source, BundleFiles.spritePath(id), SpriteFileJson.serializer(), contentIssues)
                ?.let { sprites[id] = it }
        }
        val flags = if (ContentValidator.usesFlags(manifest, layers)) {
            readAndDecode(source, BundleFiles.FLAGS, FlagsJson.serializer(), contentIssues)
        } else {
            null
        }
        if (contentIssues.isEmpty()) {
            val tones = palettes.slots.associate { it.id to BundleValidator.tones(it) }
            contentIssues.addAll(ContentValidator.validate(manifest, layers, sprites, tones, flags))
            contentIssues.addAll(VariantOverrides.bodyIssues(manifest, layers, manifest.variants))
        }
        if (contentIssues.isNotEmpty()) return BundleLoad.Invalid(contentIssues)

        val base = basePalette(palettes)
        val materials = when (val loaded = MaterialLayers.load(source, bytes, manifest, layers, base)) {
            is MaterialLayers.Result.Invalid -> return BundleLoad.Invalid(loaded.issues)
            is MaterialLayers.Result.Loaded -> loaded.layers
        }

        val bundle = build(manifest, palettes, base, layers, sprites, flags, materials)
        val stateIssues =
            ContentValidator.cachedStateIssues(bundle) + PaletteTracks.variantIssues(bundle) + MaterialParams.stateIssues(bundle)
        if (stateIssues.isNotEmpty()) return BundleLoad.Invalid(stateIssues)
        if (budgets) {
            CostBudget.validate(bundle).takeIf { it.isNotEmpty() }?.let { return BundleLoad.Invalid(it) }
        }
        MaterialFallbacks.measure(bundle)

        return BundleLoad.Loaded(bundle)
    }

    private fun <T> readAndDecode(
        source: BundleSource,
        path: String,
        strategy: DeserializationStrategy<T>,
        issues: MutableList<BundleIssue>,
        json: Json = CONTENT_JSON,
    ): T? = readJson(source, path, issues)?.let { decode(it, strategy, path, json, issues) }

    private fun basePalette(palettes: PalettesJson): Palette = Palette(
        palettes.slots.map { Palette.Slot(it.id, it.name, ramp(it.ramp, it.interpolate ?: 0), stops(it.ramp)) },
    )

    private fun build(
        manifest: ManifestJson,
        palettes: PalettesJson,
        base: Palette,
        layerFiles: Map<String, LayerFileJson>,
        spriteFiles: Map<String, SpriteFileJson>,
        flagsFile: FlagsJson?,
        materials: Map<String, MaterialLayer>,
    ): NameplateBundle {
        val interpolation = palettes.slots.associate { it.id to (it.interpolate ?: 0) }
        val flags = flagsFile?.flags.orEmpty().map { flag(it, base) }
        val flagsById = flags.associateBy { it.id }
        val colorways = BundleValidator.colorways(palettes)
        val variantSpecs = manifest.variants.ifEmpty {
            mapOf(BundleValidator.DEFAULT_COLORWAY to VariantJson(colorways.keys.first()))
        }

        val bodies = manifest.layers
            .filter { layerFiles[it.id] is BodyLayerJson }
            .associate { layer ->
                val file = layerFiles.getValue(layer.id)
                layer.id to content(file, spriteFiles, base, manifest.loop / layer.step) as NameplateBundle.LayerContent.Body
            }

        val moves = manifest.animations.values.any { PLATE_TARGET in it.tracks }
        val variants = LinkedHashMap<String, NameplateBundle.Variant>()
        for ((key, spec) in variantSpecs) {
            var palette = base
            for ((slot, stops) in colorways.getValue(spec.palette)) {
                palette = palette.with(slot, ramp(stops, interpolation.getValue(slot)), stops(stops))
            }
            val resolved = palette.resolve()
            val refs = Refs(base, resolved)
            val hidden = spec.hide.toSet()
            val merged = VariantOverrides.text(manifest.text, spec.text)
            val declared = NameplateBundle.TextMode.valueOf(merged.mode.uppercase())
            variants[key] = NameplateBundle.Variant(
                key = key,
                palette = palette,
                text = text(merged, refs),
                badge = badge(VariantOverrides.badge(manifest.badge, spec.badge), refs, bodies),
                hidden = hidden,
                swaps = spec.swap,
                flag = spec.flag?.let(flagsById::getValue) ?: flags.firstOrNull(),
                ownsBackground = VariantOverrides.ownsBackground(manifest, spec),
                params = VariantOverrides.params(manifest, spec),
                textMode = if (declared == NameplateBundle.TextMode.VANILLA && moves) NameplateBundle.TextMode.MOTION else declared,
                motion = merged.motion.takeIf { declared != NameplateBundle.TextMode.VANILLA },
            )
        }

        val molang = BundleMolang.of(manifest)
        val schedules = AnimationCompiler.schedules(manifest.animations, molang)
        val paletteTracks = PaletteTracks.load(manifest, base, molang)
        val loopChannels = AnimationCompiler.loop(manifest.animations, molang)
        val triggers = TriggerCompiler.compile(manifest.animations, molang)
        return NameplateBundle(
            id = manifest.id,
            requires = manifest.requires.toSet(),
            fps = manifest.fps,
            loop = manifest.loop,
            ownsBackground = manifest.ownsBackground,
            params = manifest.params,
            layers = manifest.layers.map {
                NameplateBundle.Layer(
                    id = it.id,
                    depth = NameplateBundle.Depth.valueOf(it.depth.uppercase()),
                    step = it.step,
                    mask = it.mask,
                    content = bodies[it.id]
                        ?: materials[it.id]
                        ?: content(layerFiles.getValue(it.id), spriteFiles, base, manifest.loop / it.step),
                    clipFill = it.clip == "fill",
                )
            },
            palette = base,
            flags = flags,
            variants = variants,
            schedules = schedules,
            paletteTracks = paletteTracks,
            perPlayerPhase = AnimationCompiler.perPlayer(manifest.phase),
            groups = AnimationCompiler.groups(manifest.groups),
            loopChannels = loopChannels,
            triggers = triggers,
            layerGates = AdaptiveCompiler.gates(manifest, layerFiles),
            bursts = AdaptiveCompiler.bursts(manifest),
            molang = molang.takeUnless { it.isEmpty },
            materialParams = MaterialParamCompiler.compile(manifest),
        )
    }

    private fun text(text: TextJson, refs: Refs): TextStyling = TextStyling(
        color = refs.paint(text.color),
        shadow = refs.paint(text.shadow),
        outline = refs.paint(text.outline),
        formatting = TextStyling.Formatting.valueOf(text.formatting.uppercase()),
        candidates = refs.colors(text.candidates),
        backdropHint = IntArray(text.backdropHint.size) { refs.rgb(text.backdropHint[it]) },
    )

    private fun content(
        file: LayerFileJson,
        sprites: Map<String, SpriteFileJson>,
        base: Palette,
        cycle: Int,
    ): NameplateBundle.LayerContent = when (file) {
        is BodyLayerJson -> NameplateBundle.LayerContent.Body(
            padLeft = file.padding[0],
            padTop = file.padding[1],
            padRight = file.padding[2],
            padBottom = file.padding[3],
            minWidth = file.minWidth,
            radius = file.radius,
            fill = file.fill?.let { fill(it, base) },
            rings = file.rings.map { fill(it, base) },
            corners = BodyCorners.of(file),
            art = file.art?.let { BodyArt.of(it, pixelSprite(sprites.getValue(it.sprite), base)) },
            aroundName = file.of == NAME_TARGET,
        )
        is SpriteLayerJson -> {
            val sprite = sprites.getValue(file.sprite)
            val colors = sprite.colors.entries.associate { (key, ref) -> key[0] to slotRef(ref, base) }
            val place = OrnamentAnchor.named(file.anchor, file.at) ?: OrnamentAnchor.of(Anchor.TOP_LEFT)
            NameplateBundle.LayerContent.Sprite(
                frames = BundleFiles.framesOf(sprite).orEmpty().map { PixelSprite(it, colors) },
                of = file.of,
                anchor = place.edge,
                pivot = file.pivot?.let(Anchor::named) ?: place.defaultPivot,
                dx = file.offset[0],
                dy = file.offset[1],
                mirror = file.mirror,
                timeline = file.timeline?.let { AnimationCompiler.timeline(it, cycle) },
                mirrorDelay = file.mirrorDelay,
                mirrorSeed = file.mirrorSeed,
                adaptive = AdaptiveCompiler.sprite(file, sprites, base, cycle),
                place = place,
                follow = file.follow?.let(EdgeFollow::named),
            )
        }
        is EmitterLayerJson -> AdaptiveCompiler.emitter(file, sprites, base)
        is MaterialLayerJson -> error("Material layers are built by MaterialLayers")
    }

    private fun pixelSprite(sprite: SpriteFileJson, base: Palette): PixelSprite {
        val colors = sprite.colors.entries.associate { (key, ref) -> key[0] to slotRef(ref, base) }
        return PixelSprite(BundleFiles.framesOf(sprite).orEmpty().first(), colors)
    }

    private fun fill(fill: FillJson, base: Palette): Fill {
        if (fill.flag) return Fill.ActiveFlag
        fill.blend?.let { return Fill.Blend(base.indexOfSlot(it)) }
        fill.sweep?.let {
            val slot = base.indexOfSlot(it)
            val axis = if (fill.axis == "y") SweepAxis.Y else SweepAxis.X
            return Fill.Sweep(slot, base.slots[slot].size, axis)
        }
        val gradient = fill.gradient
        if (gradient != null) {
            val slot = base.slots.indexOfFirst { it.id == gradient }
            return Fill.Gradient(slot, base.slots[slot].size)
        }
        return Fill.Solid(slotRef(fill.color.orEmpty(), base))
    }

    private fun flag(flag: FlagJson, base: Palette): Flag = Flag(
        id = flag.id,
        bands = IntArray(flag.bands.size) { slotRef(flag.bands[it].color, base) },
        weights = IntArray(flag.bands.size) { flag.bands[it].weight },
        overlay = flag.overlay?.let { overlay ->
            Overlay(
                kind = OverlayKind.valueOf(overlay.kind.uppercase()),
                colors = IntArray(overlay.colors.size) { slotRef(overlay.colors[it], base) },
                width = overlay.width,
            )
        },
        overrides = flag.rowsOverride.entries.associate { (rows, fitted) -> rows.toInt() to fitted.toIntArray() },
        symmetric = flag.symmetric,
        columns = flag.axis == "columns",
    )

    private fun slotRef(value: String, base: Palette): Int {
        val ref = ColorRef.parse(value) as ColorRef.Slot
        return base.ref(ref.id, ref.tone)
    }

    private fun badge(badge: BadgeJson, refs: Refs, bodies: Map<String, NameplateBundle.LayerContent.Body>): BadgeStyling {
        val placement = when (badge.placement) {
            "carried" -> BadgeStyling.Placement.CARRIED
            "placed" -> BadgeStyling.Placement.placed(
                EdgeBadgeAnchor.of(badge, bodies)
                    ?: BlockAnchor(Anchor.named(badge.anchor.orEmpty()) ?: Anchor.TOP_LEFT, badge.offset[0], badge.offset[1]),
            )
            else -> BadgeStyling.Placement.RESERVE
        }
        val tint = when (val ref = ColorRef.parse(badge.tint)) {
            is ColorRef.Slot -> BadgeStyling.Tint.slot(refs.resolved, refs.base.ref(ref.id, ref.tone))
            is ColorRef.Hex -> BadgeStyling.Tint.of(ref.argb)
            else -> BadgeStyling.Tint.NONE
        }
        val decoration = when (badge.decoration) {
            "auto" -> BadgeStyling.Decoration.AUTO
            "shadow" -> BadgeStyling.Decoration.shadow(refs.rgb(badge.decorationColor!!))
            "outline" -> BadgeStyling.Decoration.outline(refs.rgb(badge.decorationColor!!))
            else -> BadgeStyling.Decoration.NONE
        }
        return BadgeStyling(tint, decoration, placement, refs.colors(badge.candidates))
    }

    private class Refs(val base: Palette, val resolved: ResolvedPalette) {
        fun argb(value: String): Int = when (val ref = ColorRef.parse(value)) {
            is ColorRef.Hex -> ref.argb
            is ColorRef.Slot -> resolved.color(base.ref(ref.id, ref.tone))
            else -> error("'$value' is not a color")
        }

        fun rgb(value: String): Int = argb(value) and 0xFFFFFF

        fun paint(value: String): TextPaint = when (ColorRef.parse(value)) {
            ColorRef.Auto -> TextPaint.AUTO
            ColorRef.None -> TextPaint.NONE
            else -> TextPaint.of(rgb(value))
        }

        fun colors(values: List<String>?): IntArray =
            if (values == null) Contrast.DEFAULT_CANDIDATES else IntArray(values.size) { rgb(values[it]) }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        internal val CONTENT_JSON = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "kind"
        }

        fun invalid(path: String, message: String): BundleLoad = BundleLoad.Invalid(listOf(BundleIssue(path, message)))

        fun stops(stops: List<String>): IntArray = IntArray(stops.size) { HexColor.parse(stops[it])!! }

        internal fun ramp(stops: List<String>, interpolate: Int): IntArray = RampInterpolation.expand(stops(stops), interpolate)

        fun readJson(source: BundleSource, path: String, issues: MutableList<BundleIssue>): JsonElement? {
            val text = source.text(path)
            if (text == null) {
                issues.add(BundleIssue(path, "missing", IssueCode.MISSING_FILE))
                return null
            }
            if (text.length > BundleLimits.MAX_JSON_CHARS) {
                issues.add(
                    BundleIssue(
                        path,
                        "larger than ${BundleLimits.MAX_JSON_CHARS} characters",
                        IssueCode.JSON_SIZE,
                        issueValues("chars" to text.length, "limit" to BundleLimits.MAX_JSON_CHARS),
                    ),
                )
                return null
            }
            val depth = depth(text)
            if (depth > BundleLimits.MAX_JSON_DEPTH) {
                issues.add(
                    BundleIssue(
                        path,
                        "nested deeper than ${BundleLimits.MAX_JSON_DEPTH}",
                        IssueCode.JSON_DEPTH,
                        issueValues("depth" to depth, "limit" to BundleLimits.MAX_JSON_DEPTH),
                    ),
                )
                return null
            }

            return try {
                JSON.parseToJsonElement(text)
            } catch (e: SerializationException) {
                issues.add(BundleIssue(path, "not valid JSON: ${e.message}", IssueCode.JSON_SYNTAX))
                null
            }
        }

        fun <T> decode(
            element: JsonElement,
            strategy: DeserializationStrategy<T>,
            path: String,
            json: Json,
            issues: MutableList<BundleIssue>,
        ): T? = try {
            json.decodeFromJsonElement(strategy, element)
        } catch (e: IllegalArgumentException) {
            issues.add(BundleIssue(path, e.message ?: "does not match the format", IssueCode.FORMAT))
            null
        }

        fun depth(text: String): Int {
            var depth = 0
            var max = 0
            var inString = false
            var escaped = false
            for (c in text) {
                when {
                    escaped -> escaped = false
                    inString && c == '\\' -> escaped = true
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '{' || c == '[' -> max = maxOf(max, ++depth)
                    c == '}' || c == ']' -> depth--
                }
            }
            return max
        }
    }
}
