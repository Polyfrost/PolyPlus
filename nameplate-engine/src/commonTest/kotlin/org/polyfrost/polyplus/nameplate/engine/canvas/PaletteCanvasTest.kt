package org.polyfrost.polyplus.nameplate.engine.canvas

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.support.of
import org.polyfrost.polyplus.nameplate.engine.support.pixelKey
import org.polyfrost.polyplus.nameplate.engine.support.with

class PaletteCanvasTest {
    private val grid = PixelGrid()

    @Test
    fun slotCanvasResolvesUnderEachPalette() {
        val canvas = grid.paletteCanvas()
        canvas.fill(PixelRect(0, 0, 4, 2), BASE.ref("fill"))
        canvas[1, 0] = BASE.ref("edge", 0)
        canvas[2, 1] = BASE.ref("edge", 1)
        val image = canvas.compile()

        assertTrue(image.usesPalette)
        assertPixels(Rects.pixels(image.resolve(BASE.resolve())), argb(0xFF111111), argb(0xFF220000), argb(0xFF440000))
        assertPixels(Rects.pixels(image.resolve(OTHER.resolve())), argb(0xFF00FF00), argb(0xFF0000FF), argb(0xFF00FFFF))
    }

    @Test
    fun palettesShareGeometry() {
        val canvas = grid.paletteCanvas()
        canvas.fill(PixelRect(0, 0, 3, 3), BASE.ref("fill"))
        canvas[1, 1] = BASE.ref("edge")
        val image = canvas.compile()

        val base = image.resolve(BASE.resolve())
        val other = image.resolve(OTHER.resolve())
        assertEquals(base.size, other.size)
        for (i in base.indices step PixelImage.STRIDE) {
            assertContentEquals(base.copyOfRange(i, i + 4), other.copyOfRange(i, i + 4))
        }
    }

    @Test
    fun flatOverrideCoversEveryTone() {
        val resolved = BASE.with("edge", argb(0xFFABCDEF)).resolve()
        assertEquals(argb(0xFFABCDEF), resolved.color(BASE.ref("edge", 0)))
        assertEquals(argb(0xFFABCDEF), resolved.color(BASE.ref("edge", 1)))
    }

    @Test
    fun tonesPastTheRampRepeatItsLastColour() {
        val resolved = BASE.resolve()
        assertEquals(argb(0xFF440000), resolved.color(BASE.ref("edge") + Palette.MAX_TONES - 1))
    }

    @Test
    fun paletteImagesNeedAPalette() {
        val canvas = grid.paletteCanvas()
        canvas[0, 0] = BASE.ref("edge")
        val image = canvas.compile()
        assertFailsWith<IllegalStateException> { image.resolve() }
        assertFailsWith<IllegalArgumentException> { image.resolve(Palette.of(BASE.slots[0]).resolve()) }
    }

    @Test
    fun invalidPalettesAreRejected() {
        assertFailsWith<IllegalArgumentException> { Palette.of(BASE.slots[0], BASE.slots[0]) }
        assertFailsWith<IllegalArgumentException> { Palette.Slot("empty", "Empty") }
        assertFailsWith<IllegalArgumentException> { BASE.ref("fill", 1) }
        assertFailsWith<IllegalArgumentException> { BASE.ref("missing") }
    }

    private fun assertPixels(pixels: Map<Long, Int>, fill: Int, edgeDark: Int, edgeLight: Int) {
        assertEquals(8, pixels.size)
        for (y in 0 until 2) {
            for (x in 0 until 4) {
                val expected = when {
                    x == 1 && y == 0 -> edgeDark
                    x == 2 && y == 1 -> edgeLight
                    else -> fill
                }
                assertEquals(expected, pixels[pixelKey(x, y)], "pixel $x,$y")
            }
        }
    }

    private companion object {
        val BASE = Palette.of(
            Palette.Slot("fill", "Fill", argb(0xFF111111)),
            Palette.Slot("edge", "Edge", argb(0xFF220000), argb(0xFF440000)),
        )
        val OTHER = BASE.with("fill", argb(0xFF00FF00)).with("edge", argb(0xFF0000FF), argb(0xFF00FFFF))

        fun argb(value: Long): Int = value.toInt()
    }
}
