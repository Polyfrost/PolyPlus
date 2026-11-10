package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.internal.anim.Particles
import org.polyfrost.polyplus.nameplate.engine.internal.anim.alongPathX
import org.polyfrost.polyplus.nameplate.engine.internal.anim.alongPathY
import org.polyfrost.polyplus.nameplate.engine.internal.anim.framesOf
import org.polyfrost.polyplus.nameplate.engine.internal.anim.states
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpawnShape
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangBounds
import kotlin.math.abs

internal object DynamicBudget {
    private class Extent {
        private var left = Int.MAX_VALUE
        private var top = Int.MAX_VALUE
        private var right = Int.MIN_VALUE
        private var bottom = Int.MIN_VALUE

        fun add(left: Int, top: Int, right: Int, bottom: Int) {
            this.left = minOf(this.left, left)
            this.top = minOf(this.top, top)
            this.right = maxOf(this.right, right)
            this.bottom = maxOf(this.bottom, bottom)
        }

        fun breach(background: PixelRect, range: PoseRange): Int {
            if (left > right) return 0
            return maxOf(
                0,
                background.x0 - (left + range.minX),
                right + range.maxX - background.x1,
                background.y0 - (top + range.minY),
                bottom + range.maxY - background.y1,
            )
        }
    }

    fun measure(
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        block: TagBlock,
        shapes: ShapeSet,
        margins: MutableMap<String, Int>,
        molang: MolangBounds? = null,
    ): Int {
        val background = PixelRect.vanillaBackground(block.width, block.height)
        var quads = 0
        for ((_, dynamic) in DynamicSlots.layers(bundle, variant, block)) {
            val layer = dynamic.layer
            val range = PoseRange.of(bundle, layer, molang)
            val extent = Extent()
            val candidates = when (val content = dynamic.content) {
                is ParticleEmitter -> particles(layer, content, dynamic, shapes, extent)
                is LayerContent.Sprite -> walkers(bundle, layer, content, dynamic, shapes, extent)
                is LayerContent.Body, is MaterialLayer -> emptyList()
            }
            quads += dynamic.capacity * (candidates.maxOfOrNull { it.rectCount } ?: 0)
            margins[layer.id] = maxOf(margins[layer.id] ?: 0, extent.breach(background, range))
        }
        return quads
    }

    fun cachedBytes(bundle: NameplateBundle, shapes: ShapeSet): Long {
        var bytes = 0L
        for (layer in bundle.layers) {
            val content = layer.content
            val shown = when {
                content is ParticleEmitter -> content.states()
                DynamicSlots.isDynamic(layer) -> states(bundle, layer).toList()
                else -> emptyList()
            }
            for (state in shown) {
                bytes += imageBytes(shapes.sprite(layer, state).image)
            }
        }
        return bytes
    }

    private fun walkers(
        bundle: NameplateBundle,
        layer: NameplateBundle.Layer,
        sprite: LayerContent.Sprite,
        dynamic: DynamicLayer,
        shapes: ShapeSet,
        extent: Extent,
    ): List<Shape> {
        val path = checkNotNull(sprite.adaptive.path)
        val base = dynamic.alternative * SpriteAdaptive.STATE_STRIDE
        val candidates = shapes.alternative(bundle, layer, base)
        val geometry = dynamic.geometry(sprite, path)
        val pixels = sprite.framesOf(dynamic.alternative).first()
        val left = sprite.alongPathX(geometry.xs.min(), pixels)
        val right = sprite.alongPathX(geometry.xs.max(), pixels)
        val top = sprite.alongPathY(geometry.ys.min(), pixels)
        val bottom = sprite.alongPathY(geometry.ys.max(), pixels)
        for (shape in candidates.filterNot { it.isEmpty }) {
            extent.add(left + shape.x0, top + shape.y0, right + shape.x1, bottom + shape.y1)
            if (path.hasFacings) {
                extent.add(left + pixels.width - shape.x1, top + shape.y0, right + pixels.width - shape.x0, bottom + shape.y1)
            }
        }
        return candidates
    }

    private fun particles(
        layer: NameplateBundle.Layer,
        emitter: ParticleEmitter,
        dynamic: DynamicLayer,
        shapes: ShapeSet,
        extent: Extent,
    ): List<Shape> {
        val spawn = spawnExtent(emitter, dynamic)
        val moved = intArrayOf(Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE)
        for (age in 0 until emitter.life.max) {
            for (vx in intArrayOf(emitter.velocityX.min, emitter.velocityX.max)) {
                val dx = Particles.offset(vx + emitter.driftX, emitter.gravityX, age)
                moved[0] = minOf(moved[0], dx)
                moved[1] = maxOf(moved[1], dx)
            }
            for (vy in intArrayOf(emitter.velocityY.min, emitter.velocityY.max)) {
                val dy = Particles.offset(vy + emitter.driftY, emitter.gravityY, age)
                moved[2] = minOf(moved[2], dy)
                moved[3] = maxOf(moved[3], dy)
            }
        }
        val wobbleX = abs(emitter.wobble?.x ?: 0)
        val wobbleY = abs(emitter.wobble?.y ?: 0)
        val states = emitter.states()
        val candidates = states.map { shapes.sprite(layer, it) }
        for ((i, state) in states.withIndex()) {
            val shape = candidates[i]
            if (shape.isEmpty) continue
            val pixels = emitter.spriteOf(state)
            val dx = -pixels.width / 2
            val dy = -pixels.height / 2
            extent.add(
                spawn.x0 + moved[0] - wobbleX + dx + shape.x0,
                spawn.y0 + moved[2] - wobbleY + dy + shape.y0,
                spawn.x1 + moved[1] + wobbleX + dx + shape.x1,
                spawn.y1 + moved[3] + wobbleY + dy + shape.y1,
            )
        }
        return candidates
    }

    private fun spawnExtent(emitter: ParticleEmitter, dynamic: DynamicLayer): PixelRect {
        val rect = dynamic.rect
        val spawn = emitter.spawn
        val area = when (spawn.shape) {
            SpawnShape.POINT -> PixelRect(spawn.anchor.x(rect), spawn.anchor.y(rect), spawn.anchor.x(rect), spawn.anchor.y(rect))
            SpawnShape.LINE -> {
                val last = maxOf(1, spawn.edge.length(rect)) - 1
                PixelRect(spawn.edge.x(rect, 0), spawn.edge.y(rect, 0), spawn.edge.x(rect, last), spawn.edge.y(rect, last))
            }
            SpawnShape.AREA -> dynamic.spawnRows(emitter)?.let { rows ->
                val centerX = emitter.boxWidth / 2
                val centerY = emitter.boxHeight / 2
                PixelRect(
                    rows.lefts.min() + centerX,
                    rows.top + centerY,
                    rows.rights.max() + centerX,
                    rows.top + rows.count - 1 + centerY,
                )
            } ?: PixelRect(rect.x0, rect.y0, rect.x0 + maxOf(1, rect.width) - 1, rect.y0 + maxOf(1, rect.height) - 1)
            SpawnShape.BORDER -> dynamic.spawnRoute(emitter)?.let { route ->
                val centerX = emitter.boxWidth / 2
                val centerY = emitter.boxHeight / 2
                PixelRect(route.xs.min() + centerX, route.ys.min() + centerY, route.xs.max() + centerX, route.ys.max() + centerY)
            } ?: PixelRect(rect.x0, rect.y0, rect.x1, rect.y1)
        }
        return PixelRect(area.x0 + spawn.dx, area.y0 + spawn.dy, area.x1 + spawn.dx, area.y1 + spawn.dy)
    }
}
