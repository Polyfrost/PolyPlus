package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.nameplate.render.NameTagSupport

class NameTagSneakAlphaTest {
    @Test
    fun `see-through pass alpha is read from the pass colour`() {
        assertEquals(0x20, NameTagSupport.legacyPassAlpha(0x20FFFFFF))
        assertEquals(0x80, NameTagSupport.legacyPassAlpha(0x80FFFFFF.toInt()))
        assertEquals(0x14, NameTagSupport.legacyPassAlpha(0x14FFFFFF))
    }

    @Test
    fun `colours the font forces opaque read as opaque`() {
        assertEquals(0xFF, NameTagSupport.legacyPassAlpha(0x00FFFFFF))
        assertEquals(0xFF, NameTagSupport.legacyPassAlpha(0x03FFFFFF))
        assertEquals(0x04, NameTagSupport.legacyPassAlpha(0x04FFFFFF))
        assertEquals(0xFF, NameTagSupport.legacyPassAlpha(-1))
    }

    @Test
    fun `restyled text is redrawn at drawable alphas`() {
        assertTrue(NameTagSupport.redrawsText(true, 0x20))
        assertTrue(NameTagSupport.redrawsText(true, NameTagSupport.VISIBLE_ALPHA))
        assertTrue(NameTagSupport.redrawsText(true, 0x7F))
        assertTrue(NameTagSupport.redrawsText(true, 0xFF))
    }

    @Test
    fun `text too faint to redraw is left to the original pass`() {
        for (alpha in 0 until NameTagSupport.VISIBLE_ALPHA) {
            assertFalse(NameTagSupport.redrawsText(true, alpha))
        }
    }

    @Test
    fun `text that is not restyled is left to the original pass`() {
        assertFalse(NameTagSupport.redrawsText(false, 0xFF))
        assertFalse(NameTagSupport.redrawsText(false, 0x20))
    }
}
