package org.polyfrost.polyplus.nameplate.engine.internal.canvas

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.internal.strip.IntList
import org.polyfrost.polyplus.nameplate.engine.internal.strip.PixelStrips
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class PixelCanvas(
    private val grid: PixelGrid,
    private val generation: Int,
    private val palette: Boolean,
) {
    private var minX = Int.MAX_VALUE
    private var minY = Int.MAX_VALUE
    private var maxX = Int.MIN_VALUE
    private var maxY = Int.MIN_VALUE

    fun interface PixelColor {
        fun colorAt(x: Int, y: Int): Int
    }

    operator fun set(x: Int, y: Int, color: Int) {
        require(!palette || color >= 0) { "Palette canvases take palette refs, not ARGB" }

        val index = grid.index(x, y)
        if (index < 0) return

        grid.colors[index] = color
        grid.generations[index] = generation
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
    }

    fun clear(x: Int, y: Int) {
        val index = grid.index(x, y)
        if (index >= 0) {
            grid.generations[index] = 0
        }
    }

    fun fill(rect: PixelRect, color: Int) {
        roundedRect(rect, 0, color)
    }

    fun roundedRect(rect: PixelRect, radius: Int, color: Int) {
        roundedRect(rect, radius) { _, _ -> color }
    }

    fun roundedRect(rect: PixelRect, radius: Int, color: PixelColor) {
        val r = minOf(radius, minOf(rect.width, rect.height) / 2)
        for (y in rect.y0 until rect.y1) {
            val fromEdge = minOf(y - rect.y0, rect.y1 - 1 - y)
            val inset = if (fromEdge < r) cornerInset(r, fromEdge) else 0
            for (x in rect.x0 + inset until rect.x1 - inset) {
                set(x, y, color.colorAt(x, y))
            }
        }
    }

    fun sprite(sprite: PixelSprite, x: Int, y: Int, mirrorX: Boolean) {
        for (row in 0 until sprite.height) {
            for (column in 0 until sprite.width) {
                val c = sprite.cell(if (mirrorX) sprite.width - 1 - column else column, row)
                if (c != PixelSprite.TRANSPARENT) {
                    set(x + column, y + row, sprite.color(c))
                }
            }
        }
    }

    fun silhouette(sprite: PixelSprite, x: Int, y: Int, color: Int) {
        for (row in 0 until sprite.height) {
            for (column in 0 until sprite.width) {
                if (sprite.cell(column, row) != PixelSprite.TRANSPARENT) {
                    set(x + column, y + row, color)
                }
            }
        }
    }

    fun compile(strips: Boolean = false): PixelImage {
        if (minX > maxX) return PixelImage.EMPTY

        val rects = IntList()
        var open = IntList()
        var current = IntList()
        for (y in minY..maxY + 1) {
            current.clear()
            if (y <= maxY) {
                scanRow(y, current)
            }

            var o = 0
            for (c in 0 until current.size step RUN) {
                while (o < open.size && open[o] < current[c]) {
                    close(open, o, y, rects)
                    o += RUN
                }

                if (o < open.size && open[o] == current[c] && open[o + 1] == current[c + 1] &&
                    open[o + 2] == current[c + 2]
                ) {
                    current[c + 3] = open[o + 3]
                    o += RUN
                }
            }

            while (o < open.size) {
                close(open, o, y, rects)
                o += RUN
            }

            val swap = open
            open = current
            current = swap
        }

        val packed = rects.toIntArray()
        if (palette) return PixelImage(packed, null, if (strips) PixelStrips.of(packed) else PixelStrips.NONE)

        val refs = HashMap<Int, Int>()
        val colors = IntList()
        for (i in PixelImage.REF until packed.size step PixelImage.STRIDE) {
            packed[i] = refs.getOrPut(packed[i]) {
                colors.add(packed[i])
                colors.size - 1
            }
        }

        return PixelImage(packed, ResolvedPalette(colors.toIntArray()), if (strips) PixelStrips.of(packed) else PixelStrips.NONE)
    }

    private fun scanRow(y: Int, out: IntList) {
        var x = minX
        while (x <= maxX) {
            val index = grid.index(x, y)
            if (grid.generations[index] != generation) {
                x++
                continue
            }

            val color = grid.colors[index]
            val start = x
            x++
            while (x <= maxX && painted(x, y, color)) {
                x++
            }

            out.add(start)
            out.add(x)
            out.add(color)
            out.add(y)
        }
    }

    private fun painted(x: Int, y: Int, color: Int): Boolean {
        val index = grid.index(x, y)
        return grid.generations[index] == generation && grid.colors[index] == color
    }

    private companion object {
        const val RUN = 4

        fun close(open: IntList, at: Int, bottom: Int, out: IntList) {
            out.add(open[at])
            out.add(open[at + 3])
            out.add(open[at + 1])
            out.add(bottom)
            out.add(open[at + 2])
        }

        fun cornerInset(radius: Int, rowFromEdge: Int): Int {
            val dy = radius - rowFromEdge - 0.5
            return radius - sqrt(radius * radius - dy * dy).roundToInt()
        }
    }
}

internal fun PixelGrid.canvas(): PixelCanvas = PixelCanvas(this, nextGeneration(), palette = false)

internal fun PixelGrid.paletteCanvas(): PixelCanvas = PixelCanvas(this, nextGeneration(), palette = true)
