package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.FollowChains
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.paint.MaskPainter
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.internal.anim.states
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas

internal class MaskBudget(private val bundle: NameplateBundle, private val shapes: ShapeSet) {
    private val grid = PixelGrid()
    private val cache = ByteLruCache<LayerKey, PixelImage>(Long.MAX_VALUE, ::imageBytes)
    private val masks = MaskPainter(grid, cache)

    var bytes: Long = 0
        private set

    fun measure(variant: NameplateBundle.Variant, block: TagBlock, placed: List<Placed>): Int {
        if (!bundle.hasMasks) return 0
        var extra = 0
        for (entry in placed) {
            val mask = entry.layer.mask
            if (mask == null || mask == NAME_TARGET) continue
            val states = maskStates(mask, placed)
            if (states.isEmpty()) continue
            val before = cache.bytes
            var unclipped = 0
            var worst = 0
            for (image in ownImages(entry, block)) {
                unclipped = maxOf(unclipped, if (entry.layer.content is MaterialLayer) 1 else image.quadCount)
                for (ops in states) {
                    val list = ArrayList<LayerPainter.Painted>(ops.size + 1)
                    list.add(
                        LayerPainter.Painted(
                            entry.slot,
                            entry.depth,
                            entry.x,
                            entry.y,
                            entry.mirrored,
                            entry.width,
                            image,
                            LayerPose.OPAQUE,
                        ),
                    )
                    list.addAll(ops)
                    masks.apply(list, bundle, variant, block)
                    worst = maxOf(worst, list[0].image.quadCount)
                }
            }
            bytes += (cache.bytes - before) * offsets(entry.layer, mask)
            extra += maxOf(0, worst - unclipped)
        }
        return extra
    }

    private fun ownImages(entry: Placed, block: TagBlock): List<PixelImage> {
        val layer = entry.layer
        return when (val content = layer.content) {
            is MaterialLayer -> {
                val body = content.of?.let { (bundle.layer(it).content as NameplateBundle.LayerContent.Body).rect(block) }
                    ?: block.background
                val canvas = grid.paletteCanvas()
                canvas.fill(content.material.area(PixelRect(0, 0, body.width, body.height)), 0)
                listOf(canvas.compile())
            }
            is NameplateBundle.LayerContent.Sprite ->
                states(bundle, layer)
                    .filter { it - entry.base in 0 until SpriteAdaptive.STATE_STRIDE }
                    .map { shapes.sprite(layer, it).image }
            else -> listOfNotNull(entry.body)
        }
    }

    private fun maskStates(mask: String, placed: List<Placed>): List<List<LayerPainter.Painted>> {
        val index = bundle.layers.indexOfFirst { it.id == mask }
        val entries = placed.filter { it.slot == index }
        if (entries.isEmpty()) return emptyList()
        val layer = entries[0].layer
        if (layer.content !is NameplateBundle.LayerContent.Sprite) return listOf(entries.mapNotNull { m -> m.body?.let { op(m, it) } })
        val base = entries[0].base
        return states(bundle, layer).filter { it - base in 0 until SpriteAdaptive.STATE_STRIDE }.map { state ->
            val image = shapes.sprite(layer, state).image
            entries.map { op(it, image) }
        }
    }

    private fun op(entry: Placed, image: PixelImage): LayerPainter.Painted =
        LayerPainter.Painted(entry.slot, entry.depth, entry.x, entry.y, entry.mirrored, entry.width, image, LayerPose.OPAQUE)

    private fun offsets(layer: NameplateBundle.Layer, mask: String): Long {
        val own = PoseRange.own(bundle, layer)
        val range = if (FollowChains.targetOf(layer.content) == mask) own else own + PoseRange.follow(bundle, layer) + motion(mask)
        return (range.maxX - range.minX + 1L) * (range.maxY - range.minY + 1L)
    }

    private fun motion(mask: String): PoseRange {
        val layer = bundle.layer(mask)
        return PoseRange.own(bundle, layer) + PoseRange.follow(bundle, layer)
    }
}
