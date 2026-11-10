package org.polyfrost.polyplus.nameplate.engine.api.paint

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.Depth
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.DEFAULT_CACHE_BYTES
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialTextures
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.api.text.TextBackdrop
import org.polyfrost.polyplus.nameplate.engine.internal.anim.EdgeSpot
import org.polyfrost.polyplus.nameplate.engine.internal.anim.EmitterAttach
import org.polyfrost.polyplus.nameplate.engine.internal.anim.badgeFollowAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.followAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.frameAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.framesOf
import org.polyfrost.polyplus.nameplate.engine.internal.anim.paletteAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.plateAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.poseAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.tagFrame
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Fill
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Flag
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.OverlayKind
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SweepAxis
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.flag.FlagFitting
import org.polyfrost.polyplus.nameplate.engine.internal.paint.ImageNode
import org.polyfrost.polyplus.nameplate.engine.internal.paint.LayerMemo
import org.polyfrost.polyplus.nameplate.engine.internal.paint.LayerPlan
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.internal.text.BackdropRange
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangFrame

class LayerPainter(
    private val grid: PixelGrid,
    private val cache: ByteLruCache<LayerKey, PixelImage> = ByteLruCache(DEFAULT_CACHE_BYTES, ::imageBytes),
    textures: MaterialTextures = MaterialTextures.NONE,
    materialFallbacks: Boolean = true,
) {
    data class Painted(
        val index: Int,
        val depth: Depth,
        val x: Int,
        val y: Int,
        val mirrorX: Boolean,
        val width: Int,
        val image: PixelImage,
        val opacity: Int,
        val material: MaterialOp? = null,
        val clip: PixelRect? = null,
    )

    private val adaptive = AdaptiveOps(::spriteImage)
    private val target = AdaptiveOps.Target()
    private val plans = HashMap<NameplateBundle, LayerPlan>()
    private val masks = MaskPainter(grid, cache)
    private val materials = MaterialPainter(grid, cache, textures, materialFallbacks, masks)
    private val molangFrames = HashMap<NameplateBundle, MolangFrame>()
    private var reusedKey: LayerKey? = null
    private var materialImages = materialFallbacks

    var dropped: Boolean = false
        private set

    fun palette(
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        frame: Int,
        seed: Int,
        block: TagBlock? = null,
    ): ResolvedPalette {
        val phased = tagFrame(bundle, frame, seed)
        val molang = boundMolang(bundle, variant, block, phased, slots = true)
        val palette = paletteAt(bundle, variant, phased, molang)
        dropped = molang?.failed == true
        return if (dropped) variant.resolved else palette
    }

    fun plate(
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        block: TagBlock,
        frame: Int,
        seed: Int,
        state: TagState = TagState.NONE,
    ): LayerPose {
        if (bundle.plateChain.isEmpty() || state.isReduced) return LayerPose.IDENTITY
        val phased = tagFrame(bundle, frame, seed)
        return plateAt(bundle, phased, seed, state, boundMolang(bundle, variant, block, phased))
    }

    fun badgeFollow(
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        block: TagBlock,
        frame: Int,
        seed: Int,
        state: TagState = TagState.NONE,
    ): LayerPose {
        val phased = tagFrame(bundle, frame, seed)
        return badgeFollowAt(bundle, variant, phased, seed, state, boundMolang(bundle, variant, block, phased))
    }

    fun paint(
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        block: TagBlock,
        frame: Int,
        seed: Int,
        state: TagState = TagState.NONE,
        detail: Detail = Detail.FULL,
        masked: Boolean = true,
    ): List<Painted> {
        val plan = planOf(bundle)
        val bodies = plan.bodies
        val layers = plan.layers
        for (b in plan.bodyIndices) {
            bodies[b] = (layers[b].content as LayerContent.Body).rect(block)
        }
        if (plan.usesName) {
            plan.nameRect = block.nameRect()
        }
        for (s in plan.attachIndices) {
            bodies[s] = EmitterAttach.rest(bundle, layers[s].content as LayerContent.Sprite, block, plan.memos[s].edgeRoutes())
        }

        val phased = tagFrame(bundle, frame, seed)
        val molang = boundMolang(bundle, variant, block, phased)
        val painted = if (plan.opsHint > 0) ArrayList<Painted>(plan.opsHint) else ArrayList()
        val whole = block.rect()
        val slots = plan.slots(variant)
        for (index in slots.indices) {
            val resolved = slots[index]
            if (resolved == LayerPlan.HIDDEN) continue
            if (resolved == LayerPlan.MISSING) {
                val slot = layers[index]
                bundle.layer(variant.swaps[slot.id] ?: slot.id)
            }

            val layer = layers[resolved]
            val memo = plan.memos[resolved]
            if (!memo.shownAt(block.nameWidth)) continue
            when (val content = layer.content) {
                is LayerContent.Body -> {
                    val pose = poseAt(bundle, layer, phased, seed, state = state, molang = molang)
                    if (pose.visible) {
                        painted.add(paintBody(index, memo, bundle, layer, content, checkNotNull(bodies[resolved]), variant.flag, pose))
                    }
                }
                is LayerContent.Sprite -> {
                    val rect = if (content.of == null) whole else plan.body(resolved)
                    val alternative = content.adaptive.alternative(block.nameWidth)
                    val follow = followAt(bundle, layer, phased, seed, state, molang)
                    val adaptiveTarget = target.set(painted, index, bundle, layer, rect, phased, seed, state, molang, memo, follow)
                    when {
                        alternative == SpriteAdaptive.HIDDEN -> Unit
                        content.adaptive.path != null -> if (detail == Detail.FULL) {
                            adaptive.walkers(adaptiveTarget, content, alternative)
                        }
                        content.adaptive.repeat != null -> adaptive.repeats(adaptiveTarget, content, alternative)
                        else ->
                            paintSprite(
                                painted,
                                index,
                                memo,
                                bundle,
                                layer,
                                content,
                                rect,
                                block,
                                phased,
                                seed,
                                state,
                                alternative,
                                molang,
                                follow,
                            )
                    }
                }
                is ParticleEmitter -> if (detail == Detail.FULL) {
                    val rect = if (content.of == null) whole else plan.body(resolved)
                    val follow = followAt(bundle, layer, phased, seed, state, molang)
                    adaptive.particles(target.set(painted, index, bundle, layer, rect, phased, seed, state, molang, memo, follow), content)
                }
                is MaterialLayer -> {
                    val body = if (content.of == null) block.background else plan.body(resolved)
                    paintMaterial(
                        painted,
                        MaterialPainter.Target(index, bundle, variant, layer, block, phased, seed, state),
                        content,
                        body,
                        followAt(bundle, layer, phased, seed, state, molang),
                        molang,
                    )
                }
            }
        }
        if (painted.size > plan.opsHint) {
            plan.opsHint = minOf(painted.size, MAX_OPS_HINT)
        }

        dropped = molang?.failed == true
        if (dropped) return emptyList()
        if (masked && bundle.hasMasks) {
            masks.apply(painted, bundle, variant, block, plan)
        }
        return painted
    }

    fun backdrop(bundle: NameplateBundle, variant: NameplateBundle.Variant, block: TagBlock, seed: Int = 0): TextBackdrop =
        TextBackdrop { _, _, x0, y0, x1, y1, sink ->
            val range = BackdropRange()
            var anyDropped = false
            val images = materialImages
            materialImages = false
            try {
                for (frame in 0 until bundle.loop) {
                    val palette = palette(bundle, variant, frame, seed, block)
                    range.palette(palette)
                    anyDropped = anyDropped || dropped
                    for (op in paint(bundle, variant, block, frame, seed)) {
                        val clip = op.clip
                        val left = if (clip == null) x0 else maxOf(x0, if (op.mirrorX) op.x + op.width - clip.x1 else op.x + clip.x0)
                        val right = if (clip == null) x1 else minOf(x1, if (op.mirrorX) op.x + op.width - clip.x0 else op.x + clip.x1)
                        val top = if (clip == null) y0 else maxOf(y0, op.y + clip.y0)
                        val bottom = if (clip == null) y1 else minOf(y1, op.y + clip.y1)
                        if (right <= left || bottom <= top) continue
                        val material = op.material
                        if (material == null) {
                            range.image(
                                op.image,
                                BackdropRange.Placement(op.x, op.y, op.mirrorX, op.width, op.opacity),
                                left,
                                top,
                                right,
                                bottom,
                            )
                        } else {
                            materialRange(range, op, material, palette, left, top, right, bottom)
                        }
                    }
                    anyDropped = anyDropped || dropped
                }
            } finally {
                materialImages = images
            }
            dropped = anyDropped
            range.emit(sink)
        }

    private fun materialRange(
        range: BackdropRange,
        op: Painted,
        material: MaterialOp,
        palette: ResolvedPalette,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ) {
        val area = material.area
        val left = if (op.mirrorX) op.x + op.width - area.x1 else op.x + area.x0
        val top = op.y + area.y0
        if (op.opacity <= 0 || left >= x1 || left + area.width <= x0 || top >= y1 || top + area.height <= y0) return
        for (argb in material.material.backdropColors) {
            range.color(BackdropRange.scaleAlpha(argb, op.opacity))
        }
        for (ref in material.material.backdropRefs) {
            range.color(BackdropRange.scaleAlpha(palette.color(ref), op.opacity))
        }
    }

    private fun probe(bundle: NameplateBundle, layerId: String, width: Int, height: Int, flagId: String?, state: Int): LayerKey =
        reusedKey?.set(bundle, layerId, width, height, flagId, state)
            ?: LayerKey(bundle, layerId, width, height, flagId, state).also { reusedKey = it }

    private fun planOf(bundle: NameplateBundle): LayerPlan {
        plans[bundle]?.let { return it }
        if (plans.size >= MAX_PLANS) {
            plans.clear()
        }
        return LayerPlan(bundle).also { plans[bundle] = it }
    }

    private fun boundMolang(
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        block: TagBlock?,
        frame: Int,
        slots: Boolean = false,
    ): MolangFrame? {
        val molang = bundle.molang ?: return null
        if ((if (slots) molang.slotCount else molang.channelCount) <= 0) return null

        val bound = molangFrames[bundle] ?: run {
            if (molangFrames.size >= MAX_MOLANG_BUNDLES) {
                molangFrames.clear()
            }
            MolangFrame(molang, bundle.loop, bundle.fps).also { molangFrames[bundle] = it }
        }
        bound.bind(variant.params, block, frame)
        return bound
    }

    private fun paintMaterial(
        out: MutableList<Painted>,
        target: MaterialPainter.Target,
        content: MaterialLayer,
        body: PixelRect,
        follow: LayerPose,
        molang: MolangFrame?,
    ) {
        val pose = poseAt(target.bundle, target.layer, target.frame, target.seed, state = target.state, molang = molang)
        if (pose.visible) {
            out.add(materials.paint(target, content, body, pose, follow, materialImages))
        }
        if (!content.mirror) return
        val mirrorPose =
            poseAt(target.bundle, target.layer, target.frame, target.seed, mirrored = true, state = target.state, molang = molang)
        if (mirrorPose.visible) {
            out.add(materials.paint(target.mirror(), content, body, mirrorPose, follow, materialImages))
        }
    }

    private fun paintBody(
        index: Int,
        memo: LayerMemo,
        bundle: NameplateBundle,
        layer: NameplateBundle.Layer,
        body: LayerContent.Body,
        rect: PixelRect,
        flag: Flag?,
        pose: LayerPose,
    ): Painted {
        val flagId = flag?.id?.takeIf { body.fill == Fill.ActiveFlag || body.rings.any { it == Fill.ActiveFlag } }
        val image = memo.body(rect.width, rect.height, flagId)?.let(cache::recall)
            ?: cached(probe(bundle, layer.id, rect.width, rect.height, flagId, 0), { memo.keepBody(rect.width, rect.height, it) }) {
                bodyImage(body, rect, flag)
            }
        return Painted(index, layer.depth, rect.x0 + pose.dx, rect.y0 + pose.dy, false, rect.width, image, pose.opacity)
    }

    private fun bodyImage(body: LayerContent.Body, rect: PixelRect, flag: Flag?): PixelImage {
        val canvas = grid.paletteCanvas()
        val local = PixelRect(0, 0, rect.width, rect.height)
        for (i in body.rings.indices.reversed()) {
            val ring = local.expand(i + 1)
            body.corners.fittedTo(local).grownBy(i + 1).fill(canvas, ring, colorOf(body.rings[i], ring, flag))
        }
        val fill = body.fill
        if (fill == null) {
            body.corners.clear(canvas, local)
        } else {
            body.corners.fill(canvas, local, colorOf(fill, local, flag))
        }
        body.art?.paint(canvas, local.width, local.height)
        return canvas.compile(strips = sweepsAlongX(body) || body.art?.repeats == true)
    }

    private fun sweepsAlongX(body: LayerContent.Body): Boolean =
        body.fill.sweepsAlongX() || body.rings.any { it.sweepsAlongX() }

    private fun Fill?.sweepsAlongX(): Boolean = this is Fill.Sweep && axis == SweepAxis.X

    private fun paintSprite(
        out: MutableList<Painted>,
        index: Int,
        memo: LayerMemo,
        bundle: NameplateBundle,
        slot: NameplateBundle.Layer,
        layer: LayerContent.Sprite,
        rect: PixelRect,
        block: TagBlock,
        frame: Int,
        seed: Int,
        state: TagState,
        alternative: Int,
        molang: MolangFrame?,
        follow: LayerPose,
    ) {
        val frames = layer.framesOf(alternative)
        val base = alternative * SpriteAdaptive.STATE_STRIDE
        val layerFrame = frameAt(bundle, slot, frame, seed, state = state, molang = molang)
        val sprite = frames[layerFrame]
        val image = spriteImage(memo, bundle, slot, sprite, base + layerFrame)
        val depth = slot.depth
        val place = layer.place
        val edge = layer.follow
        val routes = memo.edgeRoutes()
        val spot = edge?.let { routes.spot(it, place, rect, sprite.width, sprite.height) }
        val y = (spot?.let(EdgeSpot::y) ?: (place.y(rect, block) - layer.pivot.pivotY(sprite.height))) + layer.dy + follow.dy
        val pose = poseAt(bundle, slot, frame, seed, state = state, molang = molang)
        if (pose.visible) {
            val x = (spot?.let(EdgeSpot::x) ?: (place.x(rect, block) - layer.pivot.pivotX(sprite.width))) + layer.dx + follow.dx
            out.add(Painted(index, depth, x + pose.dx, y + pose.dy, false, sprite.width, image, pose.opacity))
        }
        if (layer.mirror) {
            val mirrorPose = poseAt(bundle, slot, frame, seed, mirrored = true, state = state, molang = molang)
            if (!mirrorPose.visible) return
            val mirrorFrame = frameAt(bundle, slot, frame, seed, mirrored = true, state = state, molang = molang)
            val mirrorSprite = frames[mirrorFrame]
            val mirrorImage = if (mirrorFrame == layerFrame) image else spriteImage(memo, bundle, slot, mirrorSprite, base + mirrorFrame)
            val mirrorSpot = edge?.let { routes.spot(it, place.mirrored, rect, mirrorSprite.width, mirrorSprite.height) }
            val mirroredX = (
                mirrorSpot?.let(EdgeSpot::x)
                    ?: (place.mirrored.x(rect, block) - layer.pivot.mirrored.pivotX(mirrorSprite.width))
                ) - layer.dx
            val mirroredY = (
                mirrorSpot?.let(EdgeSpot::y)
                    ?: (place.mirrored.y(rect, block) - layer.pivot.pivotY(mirrorSprite.height))
                ) + layer.dy
            out.add(
                Painted(
                    index,
                    depth,
                    mirroredX - mirrorPose.dx + follow.dx,
                    mirroredY + mirrorPose.dy + follow.dy,
                    true,
                    mirrorSprite.width,
                    mirrorImage,
                    mirrorPose.opacity,
                ),
            )
        }
    }

    private fun spriteImage(
        memo: LayerMemo,
        bundle: NameplateBundle,
        slot: NameplateBundle.Layer,
        sprite: PixelSprite,
        layerFrame: Int,
    ): PixelImage =
        memo.state(layerFrame)?.let(cache::recall)
            ?: cached(probe(bundle, slot.id, 0, 0, null, layerFrame), { memo.keepState(layerFrame, it) }) {
                val canvas = grid.paletteCanvas()
                canvas.sprite(sprite, 0, 0, mirrorX = false)
                canvas.compile()
            }

    private inline fun cached(probe: LayerKey, keep: (ImageNode?) -> Unit, create: () -> PixelImage): PixelImage {
        val node = cache.entry(probe)
        if (node != null) {
            keep(node)
            return node.value
        }
        val created = create()
        keep(cache.store(probe.stored(), created))
        return created
    }

    private fun colorOf(fill: Fill, rect: PixelRect, flag: Flag?): PixelCanvas.PixelColor = when (fill) {
        is Fill.Solid -> PixelCanvas.PixelColor { _, _ -> fill.ref }
        is Fill.Gradient -> PixelCanvas.PixelColor { _, y ->
            fill.slot * Palette.MAX_TONES + gradientTone(y - rect.y0, rect.height, fill.tones)
        }
        is Fill.Blend -> PixelCanvas.PixelColor { _, y -> ResolvedPalette.blendRef(fill.slot, y - rect.y0, rect.height) }
        is Fill.Sweep -> PixelCanvas.PixelColor { x, y ->
            val offset = if (fill.axis == SweepAxis.X) x - rect.x0 else y - rect.y0
            fill.slot * Palette.MAX_TONES + offset.mod(fill.tones)
        }
        Fill.ActiveFlag -> flagColor(checkNotNull(flag) { "A flag fill needs a flag" }, rect)
    }

    private fun flagColor(flag: Flag, rect: PixelRect): PixelCanvas.PixelColor {
        val span = if (flag.columns) rect.width else rect.height
        val fitted = flag.rows(span) ?: IntArray(flag.bandCount) { if (it == 0) span else 0 }
        val overlay = flag.overlay
        return PixelCanvas.PixelColor { x, y ->
            val column = x - rect.x0
            val row = y - rect.y0
            val band = flag.band(FlagFitting.bandAt(fitted, if (flag.columns) column else row))
            when (overlay?.kind) {
                null -> band
                OverlayKind.CHEVRON, OverlayKind.TRIANGLE -> {
                    val stripe = FlagFitting.chevronStripe(column, row, rect.height, overlay.width, overlay.colorCount)
                    if (stripe >= 0) overlay.color(stripe) else band
                }
                OverlayKind.CIRCLE -> when (FlagFitting.circlePart(column, row, rect.width, rect.height, overlay.width)) {
                    0 -> overlay.color(0)
                    1 -> if (overlay.colorCount > 1) overlay.color(1) else band
                    else -> band
                }
            }
        }
    }

    companion object {
        private const val MAX_MOLANG_BUNDLES = 64
        private const val MAX_PLANS = 64
        private const val MAX_OPS_HINT = 1024

        internal fun gradientTone(row: Int, rows: Int, tones: Int): Int {
            if (rows <= 1 || tones <= 1) return 0
            return (row * (tones - 1) * 2 + (rows - 1)) / (2 * (rows - 1))
        }
    }
}
