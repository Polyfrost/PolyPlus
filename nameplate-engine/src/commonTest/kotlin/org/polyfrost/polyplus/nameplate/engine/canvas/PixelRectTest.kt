package org.polyfrost.polyplus.nameplate.engine.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect

class PixelRectTest {
    @Test
    fun vanillaBackgroundOfOneLine() {
        val background = PixelRect.vanillaBackground(37, PixelRect.GLYPH_HEIGHT)
        assertEquals(PixelRect(-1, -1, 37, 9), background)
        assertEquals(38, background.width)
        assertEquals(10, background.height)
    }

    @Test
    fun vanillaBackgroundOfTwoLines() {
        assertEquals(PixelRect(-1, -1, 48, 19), PixelRect.vanillaBackground(48, 18))
    }

    @Test
    fun expandGrowsEverySide() {
        assertEquals(PixelRect(-2, -2, 12, 10), PixelRect(0, 0, 10, 8).expand(2))
    }

    @Test
    fun withMinWidthKeepsWideRects() {
        val rect = PixelRect(0, 0, 20, 8)
        assertSame(rect, rect.withMinWidth(20))
        assertSame(rect, rect.withMinWidth(10))
    }

    @Test
    fun withMinWidthPutsTheOddPixelOnTheRight() {
        assertEquals(PixelRect(-2, 0, 13, 8), PixelRect(0, 0, 10, 8).withMinWidth(15))
        assertEquals(PixelRect(-3, 0, 13, 8), PixelRect(0, 0, 10, 8).withMinWidth(16))
    }
}
