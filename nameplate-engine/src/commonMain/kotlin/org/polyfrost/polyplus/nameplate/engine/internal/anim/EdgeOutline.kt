package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.CornerRadii

internal class BodyOutline(private val corners: CornerRadii, private val rings: Int) {
    fun rows(rect: PixelRect): EdgeRows? {
        val area = rect.expand(rings)
        val radii = corners.fittedTo(rect).grownBy(rings).fittedTo(area)
        val height = maxOf(0, area.height)
        val lefts = IntArray(height) { area.x0 + radii.left(area, area.y0 + it) }
        val rights = IntArray(height) { area.x1 - 1 - radii.right(area, area.y0 + it) }
        return EdgeRows.of(area.y0, lefts, rights)
    }

    companion object {
        private val SQUARE = CornerRadii.uniform(0)
        private val PLAIN = BodyOutline(SQUARE, 0)

        fun of(bundle: NameplateBundle, content: LayerContent): BodyOutline {
            val of = (content as? LayerContent.Sprite)?.of ?: (content as? ParticleEmitter)?.of ?: return PLAIN
            val body = bundle.layers.firstOrNull { it.id == of }?.content as? LayerContent.Body ?: return PLAIN
            return of(body)
        }

        fun of(body: LayerContent.Body): BodyOutline = BodyOutline(if (body.art == null) body.corners else SQUARE, body.rings.size)
    }
}

internal class EdgeRows private constructor(val top: Int, val lefts: IntArray, val rights: IntArray) {
    val count: Int
        get() = lefts.size

    val width: Int
        get() = rights.max() - lefts.min() + 1

    class Fit(val rows: EdgeRows, val width: Int, val height: Int)

    fun fit(boxWidth: Int, boxHeight: Int): Fit {
        var fitWidth = minOf(boxWidth, width)
        var fitHeight = minOf(boxHeight, count)
        var rows = eroded(fitWidth, fitHeight)
        while (rows == null) {
            fitWidth = maxOf(1, fitWidth - 1)
            fitHeight = maxOf(1, fitHeight - 1)
            rows = eroded(fitWidth, fitHeight)
        }
        return Fit(rows, fitWidth, fitHeight)
    }

    fun fitted(boxWidth: Int, boxHeight: Int): EdgeRows {
        val fit = fit(boxWidth, boxHeight)
        val rows = fit.rows
        val shift = (fit.width - boxWidth) / 2
        return EdgeRows(
            rows.top + (fit.height - boxHeight) / 2,
            IntArray(rows.count) { rows.lefts[it] + shift },
            IntArray(rows.count) { rows.rights[it] + shift },
        )
    }

    private fun eroded(width: Int, height: Int): EdgeRows? {
        val rows = count - height + 1
        if (rows <= 0) return null

        val inLefts = IntArray(rows) { row -> mostInRows(lefts, row, row + height) }
        val inRights = IntArray(rows) { row -> leastInRows(rights, row, row + height) - width + 1 }
        return of(top, inLefts, inRights)
    }

    fun grown(width: Int, height: Int): EdgeRows {
        val rows = count + height - 1
        val outLefts = IntArray(rows + 2) { Int.MAX_VALUE }
        val outRights = IntArray(rows + 2) { Int.MIN_VALUE }
        for (row in 0 until rows) {
            val left = leastInRows(lefts, row - height + 1, row + 1) - width + 1
            val right = mostInRows(rights, row - height + 1, row + 1)
            outLefts[row + 1] = minOf(outLefts[row + 1], left - 1)
            outRights[row + 1] = maxOf(outRights[row + 1], right + 1)
            for (near in intArrayOf(row, row + 2)) {
                outLefts[near] = minOf(outLefts[near], left)
                outRights[near] = maxOf(outRights[near], right)
            }
        }
        return EdgeRows(top - height, outLefts, outRights)
    }

    private fun leastInRows(values: IntArray, from: Int, until: Int): Int {
        var best = Int.MAX_VALUE
        for (row in maxOf(from, 0) until minOf(until, values.size)) {
            best = minOf(best, values[row])
        }
        return best
    }

    private fun mostInRows(values: IntArray, from: Int, until: Int): Int {
        var best = Int.MIN_VALUE
        for (row in maxOf(from, 0) until minOf(until, values.size)) {
            best = maxOf(best, values[row])
        }
        return best
    }

    companion object {
        fun of(top: Int, lefts: IntArray, rights: IntArray): EdgeRows? {
            var bestStart = 0
            var bestSize = 0
            var start = 0
            for (row in 0..lefts.size) {
                if (row < lefts.size && lefts[row] <= rights[row]) continue
                if (row - start > bestSize) {
                    bestStart = start
                    bestSize = row - start
                }
                start = row + 1
            }
            if (bestSize == 0) return null

            val end = bestStart + bestSize
            return EdgeRows(top + bestStart, lefts.copyOfRange(bestStart, end), rights.copyOfRange(bestStart, end))
        }
    }
}
