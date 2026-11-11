package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.Depth
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.paint.Detail
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.internal.anim.spriteOf
import org.polyfrost.polyplus.nameplate.engine.internal.anim.states
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas

internal fun MaterialLayer.bodyRect(bundle: NameplateBundle, block: TagBlock): PixelRect =
    of?.let { (bundle.layer(it).content as LayerContent.Body).rect(block) } ?: block.background

internal class Shape(val image: PixelImage, val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val isEmpty: Boolean
        get() = x1 <= x0 || y1 <= y0

    val rectCount: Int
        get() = image.quadCount
}

internal class Placed(
    val layer: NameplateBundle.Layer,
    val depth: Depth,
    val x: Int,
    val y: Int,
    val mirrored: Boolean,
    val width: Int,
    val body: PixelImage?,
    val slot: Int = 0,
    val base: Int = 0,
    val dynamic: DynamicSlots.Slot? = null,
) {
    fun pose(motion: SimulatedMotion, frame: Int): LayerPose =
        dynamic?.pose(motion, frame) ?: motion.pose(layer, frame, mirrored)

    fun state(motion: SimulatedMotion, frame: Int): Int = dynamic?.state(motion, frame) ?: motion.state(layer, frame, mirrored)
}

internal class ShapeSet(private val grid: PixelGrid, private val palette: ResolvedPalette) {
    private val sprites = HashMap<String, HashMap<Int, Shape>>()

    fun of(placed: Placed, state: Int): Shape {
        val body = placed.body
        if (body != null) return shapeOf(body)

        return sprite(placed.layer, placed.base + state)
    }

    fun sprite(layer: NameplateBundle.Layer, state: Int): Shape {
        val shapes = sprites.getOrPut(layer.id) { HashMap() }
        return shapes[state] ?: run {
            val sprite = when (val content = layer.content) {
                is LayerContent.Sprite -> content.spriteOf(state)
                is ParticleEmitter -> content.spriteOf(state)
                is LayerContent.Body, is MaterialLayer -> error("Only sprites and emitters have sprites")
            }
            val canvas = grid.paletteCanvas()
            canvas.sprite(sprite, 0, 0, mirrorX = false)
            shapeOf(canvas.compile()).also { shapes[state] = it }
        }
    }

    fun alternative(bundle: NameplateBundle, layer: NameplateBundle.Layer, base: Int): List<Shape> =
        states(bundle, layer).filter { it - base in 0 until SpriteAdaptive.STATE_STRIDE }.map { sprite(layer, it) }

    fun shapeOf(image: PixelImage): Shape {
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = Int.MIN_VALUE
        var y1 = Int.MIN_VALUE
        val rects = image.resolve(palette)
        for (i in rects.indices step PixelImage.STRIDE) {
            x0 = minOf(x0, rects[i])
            y0 = minOf(y0, rects[i + 1])
            x1 = maxOf(x1, rects[i + 2])
            y1 = maxOf(y1, rects[i + 3])
        }

        return Shape(image, x0, y0, x1, y1)
    }
}

internal object PlacedLayers {
    fun stripped(bundle: NameplateBundle): NameplateBundle = NameplateBundle(
        id = bundle.id,
        requires = bundle.requires,
        fps = bundle.fps,
        loop = bundle.loop,
        ownsBackground = bundle.ownsBackground,
        params = bundle.params,
        layers = bundle.layers,
        palette = bundle.palette,
        flags = bundle.flags,
        variants = bundle.variants,
        layerGates = bundle.layerGates,
    )

    fun of(
        stripped: NameplateBundle,
        painter: LayerPainter,
        variant: NameplateBundle.Variant,
        block: TagBlock,
    ): List<Placed> = painter.paint(stripped, variant, block, 0, 0, detail = Detail.REDUCED, masked = false)
        .map { painted ->
            val slot = stripped.layers[painted.index]
            val layer = stripped.layer(variant.swaps[slot.id] ?: slot.id)
            val body = painted.image.takeIf { layer.content is LayerContent.Body || layer.content is MaterialLayer }
            val alternative = (layer.content as? LayerContent.Sprite)?.adaptive?.alternative(block.nameWidth) ?: 0
            val base = alternative * SpriteAdaptive.STATE_STRIDE
            Placed(layer, painted.depth, painted.x, painted.y, painted.mirrorX, painted.width, body, painted.index, base)
        }
        .sortedBy { it.depth.ordinal }

    fun animated(
        bundle: NameplateBundle,
        stripped: NameplateBundle,
        painter: LayerPainter,
        variant: NameplateBundle.Variant,
        block: TagBlock,
    ): List<Placed> = (of(stripped, painter, variant, block) + DynamicSlots.of(bundle, variant, block))
        .sortedWith(compareBy({ it.depth.ordinal }, { it.slot }))

    fun distinct(bundle: NameplateBundle, byPalette: Boolean): List<NameplateBundle.Variant> {
        val seen = LinkedHashMap<String, NameplateBundle.Variant>()
        for (variant in bundle.variants.values) {
            val key = buildString {
                append(variant.hidden.sorted())
                append(variant.swaps.entries.sortedBy { it.key })
                append(variant.flag?.id)
                if (byPalette) {
                    append(variant.resolved.copyColors().contentToString())
                    append(variant.resolved.copyStops().contentDeepToString())
                }
            }
            seen.getOrPut(key) { variant }
        }

        return seen.values.toList()
    }
}
