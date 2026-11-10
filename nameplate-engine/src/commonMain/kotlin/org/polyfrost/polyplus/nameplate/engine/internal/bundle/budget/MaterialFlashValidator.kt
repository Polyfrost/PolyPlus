package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.materialLayers
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.paint.MaterialPainter
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.MaterialParams
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.color.Srgb
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialClock
import org.polyfrost.polyplus.nameplate.engine.internal.material.load.MaterialEvaluator
import org.polyfrost.polyplus.nameplate.engine.internal.paint.BodyEdges
import kotlin.math.abs

internal object MaterialFlashValidator {
    private const val FLASH_DELTA = 0.1
    private const val DARK_LIMIT = 0.8
    private const val RED = 0.2126
    private const val GREEN = 0.7152
    private const val BLUE = 0.0722
    private const val BYTE = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val LOOPS = 2
    private const val DRIFT_SECONDS = 10

    fun validate(bundle: NameplateBundle): List<BundleIssue> = listOfNotNull(scan(bundle, full = false).issue)

    fun worst(bundle: NameplateBundle): FlashResult = scan(bundle, full = true)

    private fun scan(bundle: NameplateBundle, full: Boolean): FlashResult {
        var worst = FlashResult.NONE
        val block = TagBlock.line(ReferencePlacements.MEDIUM, 0, ReferencePlacements.MEDIUM)
        val grid = PixelGrid()
        val variants = PlacedLayers.distinct(bundle, byPalette = true)
        for ((layer, content) in bundle.materialLayers) {
            val material = content.material
            if (!material.animated && bundle.materialParams.namesOf(layer.id) == null) continue
            val body = content.of?.let { (bundle.layer(it).content as NameplateBundle.LayerContent.Body).rect(block) }
                ?: block.background
            val area = material.area(PixelRect(0, 0, body.width, body.height))
            val edge = BodyEdges.of(bundle, content, body)
            val evaluator = MaterialEvaluator(material)
            val clock = Clock(bundle, layer, content.drift)
            for (variant in variants) {
                val placement = MaterialPainter.placementOf(block)
                val support = BooleanArray(area.width * area.height)
                val states = Array(clock.states) { state ->
                    val params = MaterialParams.at(bundle, layer, variant, state * layer.step, 0, TagState.NONE, false)
                    val frame = MaterialFrame(body, state, bundle.fps, layer.step, variant.resolved, params, placement, edge)
                    luminance(evaluator.evaluate(frame, grid), variant, area, support)
                }
                val copies = if (content.mirror) intArrayOf(0, content.mirrorDelay * layer.step) else intArrayOf(0)
                val result = flashing(supported(states, support), bundle, layer, clock, copies)
                if (!full && result.issue != null) return result
                worst = worst.worse(result)
            }
        }
        return worst
    }

    private fun luminance(image: PixelImage, variant: NameplateBundle.Variant, area: PixelRect, support: BooleanArray): DoubleArray {
        val values = DoubleArray(area.width * area.height)
        if (image.rectCount == 0) return values
        val rects = if (image.usesPalette) image.resolve(variant.resolved) else image.resolve()
        for (i in rects.indices step PixelImage.STRIDE) {
            val argb = rects[i + PixelImage.STRIDE - 1]
            val alpha = ((argb ushr ALPHA_SHIFT) and BYTE) / BYTE.toDouble()
            val lit = alpha * (
                RED * Srgb.linear((argb ushr RED_SHIFT) and BYTE) +
                    GREEN * Srgb.linear((argb ushr GREEN_SHIFT) and BYTE) +
                    BLUE * Srgb.linear(argb and BYTE)
                )
            for (y in maxOf(rects[i + 1], area.y0) until minOf(rects[i + 3], area.y1)) {
                for (x in maxOf(rects[i], area.x0) until minOf(rects[i + 2], area.x1)) {
                    val at = (y - area.y0) * area.width + (x - area.x0)
                    values[at] = lit
                    if (alpha > 0.0) {
                        support[at] = true
                    }
                }
            }
        }
        return values
    }

    private class Clock(bundle: NameplateBundle, layer: NameplateBundle.Layer, private val drift: Boolean) {
        private val step = layer.step
        private val loop = bundle.loop

        val frames: Int = if (drift) bundle.fps * DRIFT_SECONDS + bundle.fps else bundle.loop * LOOPS + bundle.fps

        val states: Int = if (drift) (frames + step - 1) / step else bundle.loop / layer.step

        fun state(frame: Int): Int = if (drift) MaterialClock.drift(maxOf(frame, 0), step) else MaterialClock.state(frame, step, loop)
    }

    private class Tiled(val means: Array<DoubleArray>, val areas: IntArray)

    private fun supported(states: Array<DoubleArray>, support: BooleanArray): Tiled {
        val pixels = support.indices.filter { support[it] }.toIntArray()
        val means = Array(states.size) { state ->
            val values = states[state]
            DoubleArray(pixels.size) { values[pixels[it]] }
        }
        return Tiled(means, IntArray(pixels.size) { 1 })
    }

    private fun flashing(
        tiled: Tiled,
        bundle: NameplateBundle,
        layer: NameplateBundle.Layer,
        clock: Clock,
        copies: IntArray,
    ): FlashResult {
        val frames = clock.frames
        val windows = IntArray(frames)
        val tiles = tiled.areas.size
        var peak = 0
        for (copy in 0 until copies.size * tiles) {
            val tile = copy % tiles
            val flashes = flashTimes(tiled.means, clock, tile, copies[copy / tiles])
            var first = 0
            var lastCounted = -1
            for (last in flashes.indices) {
                while (flashes[last] - flashes[first] >= bundle.fps) {
                    first++
                }
                peak = maxOf(peak, last - first + 1)
                if (last - first + 1 > BundleLimits.MAX_FLASHES) {
                    for (start in maxOf(flashes[last] - bundle.fps + 1, lastCounted + 1, 0)..flashes[first]) {
                        windows[start] += tiled.areas[tile]
                    }
                    lastCounted = flashes[first]
                }
            }
        }
        val flashing = windows.maxOrNull() ?: 0
        val issue = if (flashing > BundleLimits.MAX_FLASH_AREA) {
            BundleIssue(
                BundleFiles.layerPath(layer.id),
                "$flashing px flash together more than ${BundleLimits.MAX_FLASHES} times in one second " +
                    "(each pixel checked on its own for light-dark swings of 10% or more); " +
                    "the limit is ${BundleLimits.MAX_FLASHES} flashes over ${BundleLimits.MAX_FLASH_AREA} px",
                IssueCode.FLASH,
                issueValues(
                    "flashes" to peak,
                    "px" to flashing,
                    "limit" to BundleLimits.MAX_FLASHES,
                    "area" to BundleLimits.MAX_FLASH_AREA,
                ),
            )
        } else {
            null
        }
        return FlashResult(peak, flashing, issue)
    }

    private fun flashTimes(states: Array<DoubleArray>, clock: Clock, pixel: Int, delay: Int): IntArray {
        val times = ArrayList<Int>()
        var anchor = states[clock.state(-delay)][pixel]
        var armed = 0
        for (frame in 1 until clock.frames) {
            val value = states[clock.state(frame - delay)][pixel]
            val swing = value - anchor
            if (abs(swing) >= FLASH_DELTA && minOf(value, anchor) < DARK_LIMIT) {
                val direction = if (swing > 0) 1 else -1
                if (armed == -direction) {
                    times.add(frame)
                    armed = 0
                } else {
                    armed = direction
                }
                anchor = value
            }
        }
        return times.toIntArray()
    }
}
