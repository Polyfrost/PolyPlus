package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialFallback
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.materialLayers
import org.polyfrost.polyplus.nameplate.engine.api.cache.IMAGE_OVERHEAD_BYTES
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.paint.MaterialPainter
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.MaterialParams
import org.polyfrost.polyplus.nameplate.engine.internal.material.CoverageReads
import org.polyfrost.polyplus.nameplate.engine.internal.material.load.MaterialEvaluator
import org.polyfrost.polyplus.nameplate.engine.internal.paint.BodyEdges

internal object MaterialFallbacks {
    private const val SAMPLES = 4
    private const val DRIFT_SAMPLES = 8
    private const val DRIFT_PLACEMENTS = 3
    private const val PIXEL_OVERHEAD_UNITS = 8
    private const val NANOS_PER_UNIT = 0.6
    private const val NANOS_PER_MILLI = 1_000_000.0
    private const val MAX_REACH_PIXELS = 1_500_000L

    fun measure(bundle: NameplateBundle) {
        val layers = bundle.materialLayers
        if (layers.isEmpty()) return
        val grid = PixelGrid()
        val measured = layers.map { (layer, content) -> content to measure(bundle, layer, content, grid) }
        var total = measured.sumOf { it.second.animatedBytes }
        val still = HashSet<MaterialLayer>()
        for ((content, fallback) in measured.sortedByDescending { it.second.animatedBytes - it.second.frameBytes }) {
            if (total <= BundleLimits.MAX_FALLBACK_BYTES) break
            if (fallback.frames > 1 || fallback.shotStates > 0) {
                still.add(content)
                total -= fallback.animatedBytes - fallback.frameBytes
            }
        }
        for ((content, fallback) in measured) {
            content.fallback = MaterialFallback(
                fallback.frames,
                fallback.frameBytes,
                fallback.animatedBytes,
                content in still,
                fallback.rebuildMs,
                fallback.shotStates,
            )
        }
    }

    private fun measure(bundle: NameplateBundle, layer: NameplateBundle.Layer, content: MaterialLayer, grid: PixelGrid): MaterialFallback {
        val material = content.material
        val frames = MaterialParams.states(bundle, layer, content)
        val variants = bundle.variants.values
        val copies = if (material.inputs.isNotEmpty() || material.readsPalette) variants.size else 1
        val variant = variants.first()
        val evaluator = MaterialEvaluator(material)
        val textured = material.textures.isNotEmpty()
        val drifting = content.drift && material.animated
        val cycle = bundle.loop / layer.step
        val keys = HashSet<Any>()
        var frameBytes = 0L
        var rebuildNanos = 0.0
        var budget = if (textured && !drifting && material.coverageReads == CoverageReads.FRAME) MAX_REACH_PIXELS else 0L
        for (block in ReferencePlacements.ALL) {
            val body = content.bodyRect(bundle, block)
            val placement = MaterialPainter.placementOf(block)
            rebuildNanos = maxOf(rebuildNanos, rebuildNanos(material, body))
            if (keys.add(if (material.readsPlacement) placement else body.width to body.height)) {
                val edge = BodyEdges.of(bundle, content, body)
                val frame = MaterialFrame(body, 0, bundle.fps, layer.step, variant.resolved, variant.params, placement, edge)
                val sweeps = pixels(material, body) * cycle
                val states = if (sweeps <= budget) cycle else 0
                budget -= sweeps
                frameBytes +=
                    if (textured) reached(material, evaluator, frame, states) else imageBytes(evaluator.evaluate(frame, grid)).toLong()
            }
        }
        val sampler = Sampler(bundle, layer, content, variant, evaluator, grid)
        val ratio = when {
            textured -> 1.0
            drifting -> ReferencePlacements.ALL.take(DRIFT_PLACEMENTS).maxOf { sampler.peak(it, DRIFT_SAMPLES, cycle) }
            frames > 1 -> sampler.mean(ReferencePlacements.WIDEST_LINE, SAMPLES, frames)
            else -> 1.0
        }
        val shots = if (drifting) 0 else MaterialParams.shotStates(bundle, layer, content)
        val held = when {
            drifting -> (MaterialPainter.DRIFT_STATES + 1) * frames
            shots > 0 -> frames + MaterialPainter.DRIFT_STATES
            else -> frames
        }
        val animatedBytes = (frameBytes * ratio).toLong() * held
        return MaterialFallback(frames, frameBytes * copies, animatedBytes * copies, false, rebuildNanos / NANOS_PER_MILLI, shots)
    }

    private fun reached(material: Material, evaluator: MaterialEvaluator, frame: MaterialFrame, states: Int): Long = when {
        material.coverageReads == CoverageReads.FIXED -> covered(evaluator.reach(frame).toLong())
        material.coverageReads == CoverageReads.FRAME && states > 0 ->
            (0 until states).maxOf { covered(evaluator.reach(frame.copy(frame = it)).toLong()) }
        else -> bound(material, frame.body)
    }

    private class Sampler(
        private val bundle: NameplateBundle,
        private val layer: NameplateBundle.Layer,
        private val content: MaterialLayer,
        private val variant: NameplateBundle.Variant,
        private val evaluator: MaterialEvaluator,
        private val grid: PixelGrid,
    ) {
        fun mean(block: TagBlock, samples: Int, frames: Int): Double {
            val sizes = sizes(block, samples, frames)
            return sizes.sum().toDouble() / (sizes.size * sizes[0])
        }

        fun peak(block: TagBlock, samples: Int, frames: Int): Double {
            val sizes = sizes(block, samples, frames)
            return sizes.max().toDouble() / sizes[0]
        }

        private fun sizes(block: TagBlock, samples: Int, frames: Int): LongArray {
            val body = content.bodyRect(bundle, block)
            val placement = MaterialPainter.placementOf(block)
            val count = minOf(samples, frames)
            val edge = BodyEdges.of(bundle, content, body)
            return LongArray(count) { k ->
                val state = k * frames / count
                val frame = MaterialFrame(body, state, bundle.fps, layer.step, variant.resolved, variant.params, placement, edge)
                imageBytes(evaluator.evaluate(frame, grid)).toLong()
            }
        }
    }

    private fun bound(material: Material, body: PixelRect): Long = covered(pixels(material, body))

    private fun covered(pixels: Long): Long = IMAGE_OVERHEAD_BYTES + pixels * PixelImage.STRIDE * Int.SIZE_BYTES

    private fun rebuildNanos(material: Material, body: PixelRect): Double =
        (material.cost + PIXEL_OVERHEAD_UNITS).toDouble() * pixels(material, body) * NANOS_PER_UNIT

    private fun pixels(material: Material, body: PixelRect): Long {
        val grow = if (material.outline != null) 2 else 0
        val area = material.area(PixelRect(0, 0, body.width, body.height))
        return (area.width + grow).toLong() * (area.height + grow)
    }
}
