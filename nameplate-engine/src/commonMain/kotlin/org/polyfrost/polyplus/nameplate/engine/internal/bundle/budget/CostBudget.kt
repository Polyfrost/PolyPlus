package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.molang.MolangLimits
import org.polyfrost.polyplus.nameplate.engine.api.anim.TextMotion
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.bundle.materialLayers
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.internal.anim.BodyOutline
import org.polyfrost.polyplus.nameplate.engine.internal.anim.EdgeRoute
import org.polyfrost.polyplus.nameplate.engine.internal.anim.OfRects
import org.polyfrost.polyplus.nameplate.engine.internal.anim.framesOf
import org.polyfrost.polyplus.nameplate.engine.internal.anim.shownAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.states
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.BADGE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Fill
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PLATE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialInput
import org.polyfrost.polyplus.nameplate.engine.internal.material.MolangMaterialExpression
import org.polyfrost.polyplus.nameplate.engine.internal.palette.PaletteTrack
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangBounds
import kotlin.math.roundToLong

internal class CostReport(val quads: Int, val cacheBytes: Long, val margins: Map<String, Int>, val materialBuildOps: Long = 0)

internal object CostBudget {
    private const val MANIFEST = BundleFiles.MANIFEST
    private const val MATERIAL_QUADS = 1
    private const val KILOBYTE = 1024
    private const val TENTHS = 10.0
    private const val REBUILD_TARGET_MS = 0.5

    private data class BodyKey(val layerId: String, val width: Int, val height: Int, val flagId: String?)

    private class Measured(
        val shapes: ShapeSet,
        var quads: Int = 0,
        val bodies: HashMap<BodyKey, Int> = HashMap(),
        val sprites: LinkedHashMap<String, NameplateBundle.Layer> = LinkedHashMap(),
        val margins: HashMap<String, Int> = HashMap(),
        val masks: MaskBudget,
    )

    fun validate(bundle: NameplateBundle): List<BundleIssue> =
        budgetIssues(bundle).ifEmpty { FlashValidator.validate(bundle) }.ifEmpty { MaterialFlashValidator.validate(bundle) }

    fun budgetIssues(bundle: NameplateBundle, known: CostReport? = null): List<BundleIssue> {
        val cpu = CpuCost.of(bundle)
        if (cpu.total > MolangLimits.CPU_BUDGET) return listOf(BundleIssue(MANIFEST, cpu.message(), IssueCode.OVER_CPU, cpu.values()))

        val report = known ?: report(bundle)
        val issues = ArrayList<BundleIssue>()
        if (report.quads > BundleLimits.MAX_QUADS) {
            issues.add(
                BundleIssue(
                    MANIFEST,
                    "draws up to ${report.quads} quads per tag; the budget is ${BundleLimits.MAX_QUADS}",
                    IssueCode.OVER_QUADS,
                    issueValues("quads" to report.quads, "limit" to BundleLimits.MAX_QUADS),
                ),
            )
        }

        if (report.cacheBytes > BundleLimits.MAX_CACHED_BYTES) {
            issues.add(
                BundleIssue(
                    MANIFEST,
                    "caches ${report.cacheBytes} bytes of images; the budget is ${BundleLimits.MAX_CACHED_BYTES}",
                    IssueCode.OVER_CACHE,
                    issueValues("bytes" to report.cacheBytes, "limit" to BundleLimits.MAX_CACHED_BYTES),
                ),
            )
        }
        for (layer in bundle.layers) {
            val breach = report.margins[layer.id] ?: continue
            if (breach > BundleLimits.MAX_DECORATION_MARGIN) {
                issues.add(
                    BundleIssue(
                        BundleFiles.layerPath(layer.id),
                        "reaches $breach px outside the background; the limit is ${BundleLimits.MAX_DECORATION_MARGIN}",
                        IssueCode.OVER_MARGIN,
                        issueValues("px" to breach, "limit" to BundleLimits.MAX_DECORATION_MARGIN),
                    ),
                )
            }
        }
        val badge = report.margins[BADGE_TARGET] ?: 0
        if (badge > BundleLimits.MAX_DECORATION_MARGIN) {
            issues.add(
                BundleIssue(
                    "$MANIFEST#badge",
                    "the badge reaches $badge px outside the background; the limit is ${BundleLimits.MAX_DECORATION_MARGIN}",
                    IssueCode.OVER_MARGIN,
                    issueValues("px" to badge, "limit" to BundleLimits.MAX_DECORATION_MARGIN),
                ),
            )
        }
        val plate = report.margins[PLATE_TARGET] ?: 0
        if (plate > BundleLimits.MAX_DECORATION_MARGIN) {
            issues.add(
                BundleIssue(
                    "$MANIFEST#animations.$PLATE_TARGET",
                    "moves the whole plate $plate px; the limit is ${BundleLimits.MAX_DECORATION_MARGIN}",
                    IssueCode.OVER_MARGIN,
                    issueValues("px" to plate, "limit" to BundleLimits.MAX_DECORATION_MARGIN),
                ),
            )
        }

        return issues
    }

    fun report(bundle: NameplateBundle): CostReport {
        val measured = measure(bundle)
        return CostReport(measured.quads, cachedBytes(bundle, measured), measured.margins, CpuCost.materialBuildOps(bundle))
    }

    fun infos(bundle: NameplateBundle): List<BundleIssue> = bundle.materialLayers.flatMap { (layer, content) ->
        listOfNotNull(fallbackInfo(layer, content), shotInfo(layer, content))
    }

    private fun shotInfo(layer: NameplateBundle.Layer, content: MaterialLayer): BundleIssue? {
        val fallback = content.fallback
        if (fallback.shotStates <= 0 || fallback.still) return null
        val images = if (fallback.shotStates == 1) "1 image" else "${fallback.shotStates} images"
        return BundleIssue(
            BundleFiles.layerPath(layer.id),
            "its one-shots add $images that don't count against the ${BundleLimits.MAX_CACHED_STATES}-image limit; " +
                "where the GPU can't draw it, its CPU fallback rebuilds them at each frame step while a one-shot plays " +
                "and keeps only its newest few; ${rebuild(fallback.rebuildMs)}",
            IssueCode.FALLBACK_SHOTS,
            issueValues(
                "states" to fallback.shotStates,
                "limit" to BundleLimits.MAX_CACHED_STATES,
                "ms" to fallback.rebuildMs,
                "target" to REBUILD_TARGET_MS,
            ),
        )
    }

    private fun fallbackInfo(layer: NameplateBundle.Layer, content: MaterialLayer): BundleIssue {
        val fallback = content.fallback
        val frames = if (fallback.frames == 1) "1 frame" else "${fallback.frames} frames"
        val stored = if (content.drift) {
            "it drifts without repeating, so where the GPU can't draw it, its CPU fallback rebuilds it at each frame step " +
                "and keeps only its newest few frames (${kilobytes(fallback.animatedBytes)})"
        } else if (fallback.still) {
            "its CPU fallback would cache ${kilobytes(fallback.animatedBytes)} over $frames, more than the bundle's " +
                "${kilobytes(BundleLimits.MAX_FALLBACK_BYTES.toLong())} fallback cap, so where the GPU can't draw it, " +
                "it shows frame 0 only (${kilobytes(fallback.frameBytes)})"
        } else {
            "its CPU fallback caches ${kilobytes(fallback.animatedBytes)} over $frames " +
                "(the bundle's fallback cap is ${kilobytes(BundleLimits.MAX_FALLBACK_BYTES.toLong())})"
        }
        val values = issueValues(
            "bytes" to fallback.animatedBytes,
            "frames" to fallback.frames,
            "frameBytes" to fallback.frameBytes,
            "limit" to BundleLimits.MAX_FALLBACK_BYTES,
            "ms" to fallback.rebuildMs,
            "target" to REBUILD_TARGET_MS,
        )
        val code = when {
            content.drift -> IssueCode.FALLBACK_REBUILD
            fallback.still -> IssueCode.FALLBACK_STILL
            else -> IssueCode.FALLBACK_CACHE
        }
        return BundleIssue(BundleFiles.layerPath(layer.id), "$stored; ${rebuild(fallback.rebuildMs)}", code, values)
    }

    private fun rebuild(ms: Double): String {
        val tenths = (ms * TENTHS).roundToLong()
        val shown = "${tenths / TENTHS.toLong()}.${tenths % TENTHS.toLong()} ms"
        return if (ms > REBUILD_TARGET_MS) {
            "one CPU fallback frame takes about $shown to build at the widest reference name, over the " +
                "$REBUILD_TARGET_MS ms target, so the game builds it over several frames (GPU drawing is unaffected)"
        } else {
            "one CPU fallback frame takes about $shown to build at the widest reference name"
        }
    }

    private fun kilobytes(bytes: Long): String = "${(bytes + KILOBYTE - 1) / KILOBYTE} KB"

    fun cpu(bundle: NameplateBundle): Int = CpuCost.of(bundle).total

    private fun cachedBytes(bundle: NameplateBundle, measured: Measured): Long {
        var bytes = measured.bodies.values.sumOf { it.toLong() }
        for (layer in measured.sprites.values) {
            for (state in states(bundle, layer)) {
                bytes += imageBytes(measured.shapes.sprite(layer, state).image)
            }
        }

        return bytes + DynamicBudget.cachedBytes(bundle, measured.shapes) + measured.masks.bytes
    }

    fun quads(bundle: NameplateBundle): Int = measure(bundle).quads

    private fun measure(bundle: NameplateBundle): Measured {
        val stripped = PlacedLayers.stripped(bundle)
        val grid = PixelGrid()
        val painter = LayerPainter(grid, ByteLruCache<LayerKey, PixelImage>(Long.MAX_VALUE, ::imageBytes), materialFallbacks = false)
        val shapes = ShapeSet(grid, bundle.variants.values.first().resolved)
        val ranges = HashMap<String, PoseRange>()
        val mirrors = HashMap<String, PoseRange>()
        val measured = Measured(shapes, masks = MaskBudget(bundle, shapes))
        val variants = if (bundle.molang == null) PlacedLayers.distinct(bundle, byPalette = false) else bundle.variants.values
        for (variant in variants) {
            for (block in ReferencePlacements.ALL) {
                val molang = MolangBounds.of(bundle.molang, bundle.loop, bundle.fps, variant.params, block)
                val background = PixelRect.vanillaBackground(block.width, block.height)
                var quads = 0
                if (bundle.plateChain.isNotEmpty()) {
                    measured.margins[PLATE_TARGET] = maxOf(measured.margins[PLATE_TARGET] ?: 0, PoseRange.plate(bundle, molang).reach)
                }
                val placements = PlacedLayers.of(stripped, painter, variant, block)
                val extents = ArrayList<PixelRect?>(placements.size)
                for (placed in placements) {
                    val layer = placed.layer
                    val content = layer.content
                    val candidates = when (content) {
                        is LayerContent.Body -> {
                            val body = checkNotNull(placed.body)
                            measured.bodies[BodyKey(layer.id, placed.width, bodyHeight(content, block), flagId(content, variant))] =
                                imageBytes(body)
                            listOf(shapes.shapeOf(body))
                        }
                        is LayerContent.Sprite -> {
                            measured.sprites[layer.id] = layer
                            states(bundle, layer).filter { it - placed.base in 0 until SpriteAdaptive.STATE_STRIDE }
                                .map { shapes.sprite(layer, it) }
                        }
                        is ParticleEmitter -> emptyList()
                        is MaterialLayer -> listOf(materialShape(bundle, content, block))
                    }
                    quads += if (content is MaterialLayer) MATERIAL_QUADS else candidates.maxOfOrNull { it.rectCount } ?: 0
                    val range = when {
                        molang != null && placed.mirrored -> PoseRange.ofMirror(bundle, layer, molang)
                        molang != null -> PoseRange.of(bundle, layer, molang)
                        placed.mirrored -> mirrors.getOrPut(layer.id) { PoseRange.ofMirror(bundle, layer) }
                        else -> ranges.getOrPut(layer.id) { PoseRange.of(bundle, layer) }
                    }
                    extents.add(extent(placed, candidates, range))
                }
                for ((k, placed) in placements.withIndex()) {
                    val breach = breach(masked(bundle, placed, extents[k], placements, extents, block), background)
                    measured.margins[placed.layer.id] = maxOf(measured.margins[placed.layer.id] ?: 0, breach)
                }
                quads += DynamicBudget.measure(bundle, variant, block, shapes, measured.margins, molang)
                quads += measured.masks.measure(variant, block, placements)
                measured.quads = maxOf(measured.quads, quads)
            }
        }
        measured.margins[BADGE_TARGET] = badgeBreach(bundle)

        return measured
    }

    private fun badgeBreach(bundle: NameplateBundle): Int {
        var worst = 0
        for (variant in bundle.variants.values) {
            val spots = when (val anchor = variant.badge.placement.anchor) {
                null -> ReferencePlacements.BADGED.map { (block, reserved) -> block to (reserved.x0 to reserved.y0) }
                else -> ReferencePlacements.ALL.map { block -> block to (anchor.x(block) to anchor.y(block)) }
            }
            for ((block, spot) in spots) {
                val molang = MolangBounds.of(bundle.molang, bundle.loop, bundle.fps, variant.params, block)
                val range = PoseRange.badge(bundle, variant, molang)
                val (x, y) = spot
                val box = PixelRect(
                    x + range.minX,
                    y + range.minY,
                    x + BadgeStyling.CELLS_WIDE + range.maxX,
                    y + BadgeStyling.CELLS_HIGH + range.maxY,
                )
                worst = maxOf(worst, breach(box, PixelRect.vanillaBackground(block.width, block.height)))
            }
        }
        return worst
    }

    private fun materialShape(bundle: NameplateBundle, content: MaterialLayer, block: TagBlock): Shape {
        val body = content.of?.let { (bundle.layer(it).content as LayerContent.Body).rect(block) } ?: block.background
        val area = content.material.area(PixelRect(0, 0, body.width, body.height))
        return Shape(PixelImage.EMPTY, area.x0, area.y0, area.x1, area.y1)
    }

    private fun bodyHeight(content: LayerContent.Body, block: TagBlock): Int = content.rect(block).height

    private fun flagId(content: LayerContent.Body, variant: NameplateBundle.Variant): String? =
        variant.flag?.id?.takeIf { content.fill == Fill.ActiveFlag || content.rings.any { it == Fill.ActiveFlag } }

    private fun extent(placed: Placed, shapes: List<Shape>, range: PoseRange): PixelRect? {
        val drawn = shapes.filterNot { it.isEmpty }
        if (drawn.isEmpty()) return null

        val minX = drawn.minOf { it.x0 }
        val maxX = drawn.maxOf { it.x1 }
        val left = (if (placed.mirrored) placed.x + placed.width - maxX else placed.x + minX) + range.minX
        val right = (if (placed.mirrored) placed.x + placed.width - minX else placed.x + maxX) + range.maxX
        val top = placed.y + drawn.minOf { it.y0 } + range.minY
        val bottom = placed.y + drawn.maxOf { it.y1 } + range.maxY
        return PixelRect(left, top, right, bottom)
    }

    private fun masked(
        bundle: NameplateBundle,
        placed: Placed,
        extent: PixelRect?,
        placements: List<Placed>,
        extents: List<PixelRect?>,
        block: TagBlock,
    ): PixelRect? {
        val mask = placed.layer.mask
        if (mask == null || extent == null) return extent

        val region = if (mask == NAME_TARGET) {
            PixelRect(block.nameX, block.nameLineY, block.nameX + block.nameWidth, block.nameLineY + TagBlock.GLYPH_HEIGHT)
        } else {
            val index = bundle.layers.indexOfFirst { it.id == mask }
            placements.indices.filter { placements[it].slot == index }.mapNotNull { extents[it] }.reduceOrNull { a, b ->
                PixelRect(minOf(a.x0, b.x0), minOf(a.y0, b.y0), maxOf(a.x1, b.x1), maxOf(a.y1, b.y1))
            }
        } ?: return null
        val clipped =
            PixelRect(maxOf(extent.x0, region.x0), maxOf(extent.y0, region.y0), minOf(extent.x1, region.x1), minOf(extent.y1, region.y1))
        return clipped.takeIf { it.x1 > it.x0 && it.y1 > it.y0 }
    }

    private fun breach(box: PixelRect?, background: PixelRect): Int =
        if (box == null) 0 else maxOf(0, background.x0 - box.x0, box.x1 - background.x1, background.y0 - box.y0, box.y1 - background.y1)
}

internal class CpuCost(
    val molang: Int,
    val paths: Int,
    val particles: Int,
    val repeats: Int,
    val palette: Int,
    val materials: Int,
    val text: Int = 0,
) {
    val total: Int
        get() = molang + paths + particles + repeats + palette + materials + text

    fun values(): Map<String, Double> = issueValues(
        "units" to total,
        "limit" to MolangLimits.CPU_BUDGET,
        "molang" to molang,
        "paths" to paths,
        "particles" to particles,
        "repeats" to repeats,
        "palette" to palette,
        "materials" to materials,
        "text" to text,
    )

    fun message(): String {
        val parts = listOf(
            "Molang" to molang,
            "paths" to paths,
            "particles" to particles,
            "repeats" to repeats,
            "palette animation" to palette,
            "materials" to materials,
            "letter programs" to text,
        ).filter { it.second > 0 }.joinToString(", ") { "${it.first} ${it.second}" }
        return "costs up to $total CPU units per tag per frame ($parts; one unit is about one Molang instruction); " +
            "the budget is ${MolangLimits.CPU_BUDGET}"
    }

    private class Dynamic(var paths: Int = 0, var particles: Int = 0, var repeats: Int = 0, var materials: Int = 0) {
        val total: Int
            get() = paths + particles + repeats + materials
    }

    companion object {
        const val WALKER = 20
        const val REPEAT_COPY = 5
        const val PARTICLE = 18
        const val SPAWN_CHECK = 2
        const val PALETTE_FRAME = 10
        const val CYCLE_TRACK = 25
        const val KEYS_TRACK = 3
        const val MATERIAL_LAYER = 18

        fun of(bundle: NameplateBundle): CpuCost {
            var worst = Dynamic()
            for (variant in PlacedLayers.distinct(bundle, byPalette = false)) {
                for (block in ReferencePlacements.ALL) {
                    val dynamic = dynamic(bundle, variant, block)
                    if (dynamic.total > worst.total) {
                        worst = dynamic
                    }
                }
            }
            return CpuCost(
                (bundle.molang?.cost ?: 0) + materialMolang(bundle),
                worst.paths,
                worst.particles,
                worst.repeats,
                palette(bundle),
                worst.materials,
                TextMotion.of(bundle).cost,
            )
        }

        fun materialBuildOps(bundle: NameplateBundle): Long {
            var worst = 0L
            for ((_, content) in bundle.materialLayers) {
                for (block in ReferencePlacements.ALL) {
                    val body =
                        content.of?.let { (bundle.layer(it).content as LayerContent.Body).rect(block) } ?: block.background
                    val area = content.material.area(PixelRect(0, 0, body.width, body.height))
                    worst = maxOf(worst, content.material.cost.toLong() * area.width * area.height)
                }
            }
            return worst
        }

        private fun dynamic(bundle: NameplateBundle, variant: NameplateBundle.Variant, block: TagBlock): Dynamic {
            val cost = Dynamic()
            for (slot in bundle.layers) {
                if (slot.id in variant.hidden) continue
                val layer = bundle.layer(variant.swaps[slot.id] ?: slot.id)
                if (!bundle.shownAt(layer.id, block.nameWidth)) continue
                when (val content = layer.content) {
                    is LayerContent.Sprite -> sprite(bundle, content, block, cost)
                    is ParticleEmitter -> cost.particles += DynamicSlots.maxLive(bundle, layer, content) * PARTICLE +
                        spawnChecks(content) * SPAWN_CHECK
                    is MaterialLayer -> cost.materials += if (content.mirror) 2 * MATERIAL_LAYER else MATERIAL_LAYER
                    is LayerContent.Body -> Unit
                }
            }
            return cost
        }

        private fun sprite(bundle: NameplateBundle, content: LayerContent.Sprite, block: TagBlock, cost: Dynamic) {
            if (content.adaptive.alternative(block.nameWidth) == SpriteAdaptive.HIDDEN) return
            val path = content.adaptive.path
            val repeat = content.adaptive.repeat
            if (path != null) {
                cost.paths += path.walkers * WALKER
            } else if (repeat != null) {
                val rect = OfRects.rect(bundle, content, block)
                val pixels = content.framesOf(content.adaptive.alternative(block.nameWidth)).first()
                val run = content.follow?.let {
                    EdgeRoute.of(it, repeat.side, BodyOutline.of(bundle, content), rect, pixels.width, pixels.height).length
                }
                cost.repeats += repeat.count(rect, pixels.width, pixels.height, run ?: repeat.edge.length(rect)) * REPEAT_COPY
            }
        }

        private fun spawnChecks(emitter: ParticleEmitter): Int =
            if (emitter.every <= 0) 0 else ((emitter.life.max + emitter.jitter) / emitter.every + 1) * emitter.count

        private fun palette(bundle: NameplateBundle): Int {
            val tracks = bundle.paletteTracks
            if (tracks.isEmpty()) return 0
            return PALETTE_FRAME + tracks.sumOf { if (it is PaletteTrack.Cycle) CYCLE_TRACK else KEYS_TRACK }
        }

        private fun materialMolang(bundle: NameplateBundle): Int = bundle.materialLayers.sumOf { (_, layer) ->
            layer.material.inputs.sumOf { input ->
                ((input as? MaterialInput.Expression)?.expression as? MolangMaterialExpression)?.cost ?: 0
            }
        }
    }
}
