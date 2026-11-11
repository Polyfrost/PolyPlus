package org.polyfrost.polyplus.nameplate.engine.api.paint

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.paint.LayerPlan

internal class MaskPainter(
    private val grid: PixelGrid,
    private val cache: ByteLruCache<LayerKey, PixelImage>,
) {
    private var cover = IntArray(INITIAL)
    private var count = 0
    private val bounds = IntArray(4)
    private var reusedKey: LayerKey? = null

    fun apply(
        painted: MutableList<LayerPainter.Painted>,
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        block: TagBlock,
        plan: LayerPlan? = null,
    ) {
        val slots = plan?.slots(variant)
        var i = 0
        while (i < painted.size) {
            val op = painted[i]
            val resolved = slots?.get(op.index) ?: -1
            val layer = if (resolved >= 0) {
                bundle.layers[resolved]
            } else {
                val slot = bundle.layers[op.index]
                bundle.layer(variant.swaps[slot.id] ?: slot.id)
            }
            val mask = layer.mask
            if (mask == null) {
                i++
                continue
            }
            if (mask == NAME_TARGET) {
                painted[i] = op.copy(clip = nameClip(op, block))
                i++
                continue
            }
            val maskIndex = if (resolved >= 0) checkNotNull(plan).maskIndices[resolved] else bundle.layers.indexOfFirst { it.id == mask }
            val fill = if (layer.clipFill) fillImage(bundle, variant, maskIndex, block) else null
            val material = op.material
            if (material != null) {
                val area = material.area
                bounds[0] = area.x0
                bounds[1] = area.y0
                bounds[2] = area.x1
                bounds[3] = area.y1
                collect(op, painted, maskIndex, fill)
                val region = region(bundle, layer.id)
                when {
                    region == null -> i++
                    region.rectCount == 0 -> painted.removeAt(i)
                    else -> {
                        val image = if (op.image.rectCount == 0) op.image else clip(bundle, layer.id, op.image, region)
                        painted[i] = op.copy(image = image, material = material.clipped(region))
                        i++
                    }
                }
            } else {
                if (op.image.rectCount > 0) {
                    boundsOf(op.image)
                    collect(op, painted, maskIndex, fill)
                    painted[i] = op.copy(image = clipped(bundle, layer.id, op.image))
                }
                i++
            }
        }
    }

    fun clip(bundle: NameplateBundle, layerId: String, source: PixelImage, region: PixelImage): PixelImage {
        if (source.rectCount == 0) return source
        boundsOf(source)
        count = 0
        val rects = region.geometry()
        for (r in rects.indices step PixelImage.STRIDE) {
            add(rects[r], rects[r + 1], rects[r + 2], rects[r + 3])
        }
        return clipped(bundle, layerId, source)
    }

    private fun nameClip(op: LayerPainter.Painted, block: TagBlock): PixelRect {
        val x0 = block.nameX
        val x1 = block.nameX + block.nameWidth
        val y0 = block.nameLineY - op.y
        val y1 = block.nameLineY + TagBlock.GLYPH_HEIGHT - op.y
        return if (op.mirrorX) {
            PixelRect(op.x + op.width - x1, y0, op.x + op.width - x0, y1)
        } else {
            PixelRect(x0 - op.x, y0, x1 - op.x, y1)
        }
    }

    private fun fillImage(bundle: NameplateBundle, variant: NameplateBundle.Variant, index: Int, block: TagBlock): PixelImage? {
        val slot = bundle.layers.getOrNull(index) ?: return null
        val id = variant.swaps[slot.id] ?: slot.id
        val body = bundle.layer(id).content as? LayerContent.Body ?: return null
        val rect = body.rect(block)
        val local = PixelRect(0, 0, rect.width, rect.height)
        val probe = reusedKey?.set(bundle, id, local.width, local.height, FILL, 0)
            ?: LayerKey(bundle, id, local.width, local.height, FILL, 0).also { reusedKey = it }
        return cache.getOrPut(probe, LayerKey::stored) {
            val canvas = grid.paletteCanvas()
            if (body.fill != null) {
                body.corners.fill(canvas, local) { _, _ -> 0 }
            }
            body.art?.paint(canvas, local.width, local.height)
            canvas.compile()
        }
    }

    private fun collect(op: LayerPainter.Painted, painted: List<LayerPainter.Painted>, index: Int, fill: PixelImage?) {
        count = 0
        for (m in painted) {
            if (m.index != index || m.material != null) continue
            val rects = (fill ?: m.image).geometry()
            for (r in rects.indices step PixelImage.STRIDE) {
                val x0 = if (m.mirrorX) m.x + m.width - rects[r + 2] else m.x + rects[r]
                val x1 = if (m.mirrorX) m.x + m.width - rects[r] else m.x + rects[r + 2]
                local(op, x0, m.y + rects[r + 1], x1, m.y + rects[r + 3])
            }
        }
    }

    private fun local(op: LayerPainter.Painted, x0: Int, y0: Int, x1: Int, y1: Int) {
        if (op.mirrorX) {
            add(op.x + op.width - x1, y0 - op.y, op.x + op.width - x0, y1 - op.y)
        } else {
            add(x0 - op.x, y0 - op.y, x1 - op.x, y1 - op.y)
        }
    }

    private fun add(x0: Int, y0: Int, x1: Int, y1: Int) {
        val left = maxOf(x0, bounds[0])
        val top = maxOf(y0, bounds[1])
        val right = minOf(x1, bounds[2])
        val bottom = minOf(y1, bounds[3])
        if (right <= left || bottom <= top) return
        if (count + 4 > cover.size) {
            cover = cover.copyOf(cover.size * 2)
        }
        cover[count++] = left
        cover[count++] = top
        cover[count++] = right
        cover[count++] = bottom
    }

    private fun boundsOf(image: PixelImage) {
        val rects = image.geometry()
        bounds[0] = Int.MAX_VALUE
        bounds[1] = Int.MAX_VALUE
        bounds[2] = Int.MIN_VALUE
        bounds[3] = Int.MIN_VALUE
        for (r in rects.indices step PixelImage.STRIDE) {
            bounds[0] = minOf(bounds[0], rects[r])
            bounds[1] = minOf(bounds[1], rects[r + 1])
            bounds[2] = maxOf(bounds[2], rects[r + 2])
            bounds[3] = maxOf(bounds[3], rects[r + 3])
        }
    }

    private fun coversBounds(): Boolean =
        count == 4 && cover[0] == bounds[0] && cover[1] == bounds[1] && cover[2] == bounds[2] && cover[3] == bounds[3]

    private fun clipped(bundle: NameplateBundle, layerId: String, source: PixelImage): PixelImage = when {
        count == 0 -> PixelImage.EMPTY
        coversBounds() -> source
        else -> cache.getOrPut(probe(bundle, layerId, MASKED, source), LayerKey::stored) { build(source) }
    }

    private fun region(bundle: NameplateBundle, layerId: String): PixelImage? = when {
        count == 0 -> PixelImage.EMPTY
        coversBounds() -> null
        else -> cache.getOrPut(probe(bundle, layerId, REGION, null), LayerKey::stored) { buildRegion() }
    }

    private fun probe(bundle: NameplateBundle, layerId: String, kind: String, source: PixelImage?): LayerKey =
        reusedKey?.set(bundle, layerId, 0, 0, kind, 0, source = source, clip = cover, clipSize = count)
            ?: LayerKey(bundle, layerId, 0, 0, kind, 0, source = source, clip = cover, clipSize = count).also { reusedKey = it }

    private fun build(source: PixelImage): PixelImage {
        val x0 = bounds[0]
        val y0 = bounds[1]
        val width = bounds[2] - x0
        val covered = covered()
        val rects = if (source.usesPalette) source.geometry() else source.resolve()
        val canvas = if (source.usesPalette) grid.paletteCanvas() else grid.canvas()
        for (r in rects.indices step PixelImage.STRIDE) {
            val color = rects[r + PixelImage.REF]
            for (y in rects[r + 1] until rects[r + 3]) {
                for (x in rects[r] until rects[r + 2]) {
                    if (covered[(y - y0) * width + (x - x0)]) {
                        canvas[x, y] = color
                    }
                }
            }
        }
        return canvas.compile(strips = source.stripCount > 0)
    }

    private fun buildRegion(): PixelImage {
        val canvas = grid.canvas()
        for (c in 0 until count step 4) {
            canvas.fill(PixelRect(cover[c], cover[c + 1], cover[c + 2], cover[c + 3]), REGION_COLOR)
        }
        return canvas.compile()
    }

    private fun covered(): BooleanArray {
        val x0 = bounds[0]
        val y0 = bounds[1]
        val width = bounds[2] - x0
        val covered = BooleanArray(width * (bounds[3] - y0))
        for (c in 0 until count step 4) {
            for (y in cover[c + 1] until cover[c + 3]) {
                for (x in cover[c] until cover[c + 2]) {
                    covered[(y - y0) * width + (x - x0)] = true
                }
            }
        }
        return covered
    }

    private companion object {
        const val INITIAL = 64
        const val MASKED = "\$masked"
        const val REGION = "\$region"
        const val FILL = "\$fill"
        const val REGION_COLOR = -1
    }
}
