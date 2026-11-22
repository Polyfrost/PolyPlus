package org.polyfrost.polyplus.nameplate.engine.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas
import org.polyfrost.polyplus.nameplate.engine.support.pixelKey

class PixelSpriteTest {
    private val grid = PixelGrid()

    @Test
    fun paintsOpaqueCellsFromItsPalette() {
        val canvas = grid.canvas()
        canvas.sprite(ARROW, 10, 20, mirrorX = false)
        val pixels = Rects.pixels(canvas.compile().resolve())
        assertEquals(4, pixels.size)
        assertEquals(RED, pixels[pixelKey(10, 20)])
        assertEquals(BLUE, pixels[pixelKey(11, 20)])
        assertEquals(RED, pixels[pixelKey(10, 21)])
        assertEquals(RED, pixels[pixelKey(12, 21)])
    }

    @Test
    fun mirroringFlipsWithinItsOwnBounds() {
        val canvas = grid.canvas()
        canvas.sprite(ARROW, 10, 20, mirrorX = true)
        val pixels = Rects.pixels(canvas.compile().resolve())
        assertEquals(RED, pixels[pixelKey(12, 20)])
        assertEquals(BLUE, pixels[pixelKey(11, 20)])
        assertEquals(RED, pixels[pixelKey(10, 21)])
        assertEquals(RED, pixels[pixelKey(12, 21)])
    }

    @Test
    fun malformedSpritesAreRejected() {
        assertFailsWith<IllegalArgumentException> { PixelSprite.of(mapOf('r' to RED), "rr.", "r") }
        assertFailsWith<IllegalArgumentException> { PixelSprite.of(mapOf('r' to RED), "rx") }
        assertFailsWith<IllegalArgumentException> { PixelSprite.of(mapOf('r' to RED)) }
    }

    private companion object {
        val RED = 0xFFFF0000.toInt()
        val BLUE = 0xFF0000FF.toInt()
        val ARROW = PixelSprite.of(mapOf('r' to RED, 'b' to BLUE), "rb.", "r.r")
    }
}
