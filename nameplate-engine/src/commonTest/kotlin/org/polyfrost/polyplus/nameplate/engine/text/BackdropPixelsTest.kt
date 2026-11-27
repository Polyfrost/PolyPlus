package org.polyfrost.polyplus.nameplate.engine.text

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.text.BackdropPixels
import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.api.text.TextBackdrop
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.support.of

class BackdropPixelsTest {
    private val grid = PixelGrid()

    @Test
    fun collectsEveryFrameOfTheLoop() {
        val backdrop = loop(4, { width, _, frame ->
            val canvas = grid.paletteCanvas()
            canvas.fill(PixelRect(0, 0, width, 8), PALETTE.ref("body"))
            canvas[if (frame == 2) 3 else width + 5, 4] = PALETTE.ref("spark")
            canvas.compile()
        }, PALETTE.resolve())

        val pixels = BackdropPixels.collect(backdrop, 20, 8, 0, 0, 20, 8, NO_HINT)
        assertEquals(setOf(0xFFE3A0, 0x202020), pixels.toSet())
    }

    @Test
    fun repeatsAreCollectedOnce() {
        val backdrop = TextBackdrop { _, _, _, _, _, _, sink -> repeat(50) { sink(0xFF336699.toInt()) } }
        assertContentEquals(intArrayOf(0x336699), BackdropPixels.collect(backdrop, 10, 8, 0, 0, 10, 8, NO_HINT))
    }

    @Test
    fun translucentPixelsAreFlattenedOntoTheBackdrop() {
        val canvas = grid.paletteCanvas()
        canvas.fill(PixelRect(0, 0, 10, 8), PALETTE.ref("glow"))
        val image = canvas.compile()
        val backdrop = loop(1, { _, _, _ -> image }, PALETTE.resolve())

        val expected = Contrast.compositeOver(0x80FFFFFF.toInt(), BackdropPixels.BACKDROP)
        assertContentEquals(intArrayOf(expected), BackdropPixels.collect(backdrop, 10, 8, 0, 0, 10, 8, NO_HINT))
    }

    @Test
    fun transparentAndEmptyCellsAreSkipped() {
        val transparent = TextBackdrop { _, _, _, _, _, _, sink -> sink(0x00FFFFFF) }
        assertContentEquals(IntArray(0), BackdropPixels.collect(transparent, 10, 8, 0, 0, 10, 8, NO_HINT))

        val canvas = grid.paletteCanvas()
        canvas.fill(PixelRect(30, 0, 40, 8), PALETTE.ref("body"))
        val image = canvas.compile()
        val elsewhere = loop(3, { _, _, _ -> image }, PALETTE.resolve())
        assertContentEquals(IntArray(0), BackdropPixels.collect(elsewhere, 10, 8, 0, 0, 10, 8, NO_HINT))
    }

    @Test
    fun readsOnlyTheRequestedArea() {
        var seen = IntArray(0)
        val backdrop = TextBackdrop { _, _, x0, y0, x1, y1, _ -> seen = intArrayOf(x0, y0, x1, y1) }

        BackdropPixels.collect(backdrop, 40, 8, 7, 0, 31, 8, NO_HINT)
        assertContentEquals(intArrayOf(7, 0, 31, 8), seen)
    }

    @Test
    fun hintIsAddedAsOpaqueColours() {
        val backdrop = TextBackdrop { _, _, _, _, _, _, sink -> sink(0xFF102030.toInt()) }
        val hint = intArrayOf(0xFFF1F4F8.toInt(), 0x4C97E3, 0x102030)
        val pixels = BackdropPixels.collect(backdrop, 10, 8, 0, 0, 10, 8, hint)
        assertEquals(setOf(0x102030, 0xF1F4F8, 0x4C97E3), pixels.toSet())
        assertEquals(3, pixels.size)
    }

    private fun loop(frames: Int, image: (Int, Int, Int) -> PixelImage, palette: ResolvedPalette): TextBackdrop =
        TextBackdrop { width, height, x0, y0, x1, y1, sink ->
            for (frame in 0 until frames) {
                image(width, height, frame).sample(palette, x0, y0, x1, y1, sink)
            }
        }

    private companion object {
        val NO_HINT = IntArray(0)
        val PALETTE = Palette.of(
            Palette.Slot("body", "Body", 0xFFFFE3A0.toInt()),
            Palette.Slot("spark", "Spark", 0xFF202020.toInt()),
            Palette.Slot("glow", "Glow", 0x80FFFFFF.toInt()),
        )
    }
}
