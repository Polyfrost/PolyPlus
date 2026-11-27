package org.polyfrost.polyplus.nameplate.engine.canvas

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas
import org.polyfrost.polyplus.nameplate.engine.support.pixelKey

class ArgbCanvasTest {
    private val grid = PixelGrid()

    @Test
    fun argbCanvasCompilesToTheSameRects() {
        val canvas = grid.canvas()
        canvas.fill(PixelRect(0, 0, 4, 3), A)
        canvas.fill(PixelRect(1, 1, 3, 2), B)
        canvas[5, 0] = A
        canvas.fill(PixelRect(7, 0, 8, 3), C)
        val image = canvas.compile()

        val expected = intArrayOf(
            0, 0, 4, 1, A,
            5, 0, 6, 1, A,
            0, 1, 1, 2, A,
            1, 1, 3, 2, B,
            3, 1, 4, 2, A,
            0, 2, 4, 3, A,
            7, 0, 8, 3, C,
        )
        assertEquals(7, image.rectCount)
        assertContentEquals(expected, image.resolve())
        Rects.pixels(image.resolve())
    }

    @Test
    fun aNewCanvasStartsEmptyOnTheSameGrid() {
        grid.canvas().fill(PixelRect(0, 0, 4, 4), A)
        assertSame(PixelImage.EMPTY, grid.canvas().compile())
    }

    @Test
    fun clearedPixelsAreLeftOut() {
        val canvas = grid.canvas()
        canvas.fill(PixelRect(0, 0, 3, 1), A)
        canvas.clear(1, 0)
        assertContentEquals(intArrayOf(0, 0, 1, 1, A, 2, 0, 3, 1, A), canvas.compile().resolve())
    }

    @Test
    fun pixelsOutsideTheWindowAreDropped() {
        val canvas = grid.canvas()
        canvas[PixelGrid.WINDOW.x1, 0] = A
        canvas[0, PixelGrid.WINDOW.y0 - 1] = A
        assertSame(PixelImage.EMPTY, canvas.compile())
    }

    @Test
    fun roundedCornersStepInward() {
        val canvas = grid.canvas()
        canvas.roundedRect(PixelRect(0, 0, 6, 6), 2) { _, _ -> A }
        val pixels = Rects.pixels(canvas.compile().resolve())
        assertEquals(32, pixels.size)
        for ((x, y) in listOf(0 to 0, 5 to 0, 0 to 5, 5 to 5)) {
            assertEquals(null, pixels[pixelKey(x, y)], "corner $x,$y")
        }
    }

    @Test
    fun paletteCanvasesRejectArgb() {
        assertFailsWith<IllegalArgumentException> { grid.paletteCanvas()[0, 0] = A }
    }

    private companion object {
        val A = 0xFFFF0000.toInt()
        val B = 0x8000FF00.toInt()
        val C = 0xFF0000FF.toInt()
    }
}
