package org.polyfrost.polyplus.nameplate.engine.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.support.of
import org.polyfrost.polyplus.nameplate.engine.support.with

class PixelImageSampleTest {
    private val grid = PixelGrid()

    @Test
    fun feedsOnlyRectsOverlappingTheArea() {
        val canvas = grid.paletteCanvas()
        canvas.fill(PixelRect(0, 0, 4, 2), PALETTE.ref("solid"))
        canvas.fill(PixelRect(4, 0, 6, 2), PALETTE.ref("glass"))
        canvas.fill(PixelRect(10, 0, 12, 2), PALETTE.ref("far"))
        val image = canvas.compile()

        assertEquals(setOf(SOLID, GLASS), colours(image, PALETTE.resolve(), 3, 0, 5, 8))
    }

    @Test
    fun halfOpenEdgesDoNotCount() {
        val canvas = grid.paletteCanvas()
        canvas.fill(PixelRect(0, 0, 4, 2), PALETTE.ref("solid"))
        canvas.fill(PixelRect(4, 0, 6, 2), PALETTE.ref("far"))
        val image = canvas.compile()

        assertEquals(setOf(FAR), colours(image, PALETTE.resolve(), 4, 0, 6, 8))
        assertEquals(setOf(SOLID), colours(image, PALETTE.resolve(), 0, 0, 4, 8))
    }

    @Test
    fun emptyCellsFeedNothing() {
        val canvas = grid.paletteCanvas()
        canvas.fill(PixelRect(0, 0, 2, 2), PALETTE.ref("solid"))
        val image = canvas.compile()

        assertTrue(colours(image, PALETTE.resolve(), 5, 0, 9, 8).isEmpty())
        assertTrue(colours(PixelImage.EMPTY, PALETTE.resolve(), 0, 0, 9, 8).isEmpty())
    }

    @Test
    fun colorwaysSampleTheirOwnColours() {
        val canvas = grid.paletteCanvas()
        canvas.fill(PixelRect(0, 0, 4, 4), PALETTE.ref("solid"))
        val image = canvas.compile()

        val other = PALETTE.with("solid", 0xFFABCDEF.toInt())
        assertEquals(setOf(0xFFABCDEF.toInt()), colours(image, other.resolve(), 0, 0, 4, 8))
    }

    @Test
    fun argbImagesSampleTheirOwnTable() {
        val canvas = grid.canvas()
        canvas.fill(PixelRect(0, 0, 3, 8), 0xFF00FF00.toInt())
        canvas.fill(PixelRect(3, 0, 6, 8), 0x40FF0000)
        val image = canvas.compile()

        val fed = HashSet<Int>()
        image.sample(0, 0, 6, 8) { fed.add(it) }
        assertEquals(setOf(0xFF00FF00.toInt(), 0x40FF0000), fed)

        val paletteCanvas = grid.paletteCanvas()
        paletteCanvas[0, 0] = PALETTE.ref("solid")
        val paletteImage = paletteCanvas.compile()
        assertFailsWith<IllegalStateException> { paletteImage.sample(0, 0, 1, 1) { fed.add(it) } }
    }

    private fun colours(image: PixelImage, palette: ResolvedPalette, x0: Int, y0: Int, x1: Int, y1: Int): Set<Int> {
        val fed = HashSet<Int>()
        image.sample(palette, x0, y0, x1, y1) { fed.add(it) }
        return fed
    }

    private companion object {
        val SOLID = 0xFF112233.toInt()
        val GLASS = 0x80FFFFFF.toInt()
        val FAR = 0xFF445566.toInt()
        val PALETTE = Palette.of(
            Palette.Slot("solid", "Solid", SOLID),
            Palette.Slot("glass", "Glass", GLASS),
            Palette.Slot("far", "Far", FAR),
        )
    }
}
