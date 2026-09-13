package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.assets.detectVerticalTextureFrameCount

class CosmeticTextureFrameCountTest {
    @Test
    fun `a sheet whose UVs address frame 0 still reads as a sheet`() {
        assertEquals(4, detectVerticalTextureFrameCount(64, 64, 64, 256, 60f))
        assertEquals(8, detectVerticalTextureFrameCount(128, 128, 128, 1024, 73f))
        assertEquals(3, detectVerticalTextureFrameCount(64, 64, 64, 192, 60f))
    }

    @Test
    fun `UVs past the declared height mean the declared height is wrong, not a sheet`() {
        assertEquals(1, detectVerticalTextureFrameCount(64, 32, 64, 64, 48f))
        assertEquals(1, detectVerticalTextureFrameCount(64, 32, 64, 64, 32.5f))
        assertEquals(1, detectVerticalTextureFrameCount(64, 32, 64, 96, 48f))
    }

    @Test
    fun `exactly two frames is too ambiguous to call and reads as one texture`() {
        assertEquals(1, detectVerticalTextureFrameCount(64, 32, 64, 64, 30f))
        assertEquals(1, detectVerticalTextureFrameCount(64, 64, 64, 128, 60f))
        assertEquals(1, detectVerticalTextureFrameCount(128, 128, 128, 256, 73f))
    }

    @Test
    fun `the non-sheet cases are unchanged`() {
        assertEquals(1, detectVerticalTextureFrameCount(64, 64, 64, 64, 60f), "same size is one frame")
        assertEquals(1, detectVerticalTextureFrameCount(64, 64, 32, 256, 60f), "a different width is not a strip")
        assertEquals(1, detectVerticalTextureFrameCount(64, 64, 64, 200, 60f), "a ragged multiple is not a strip")
        assertEquals(1, detectVerticalTextureFrameCount(64, 0, 64, 256, 0f), "a declared height of zero divides by zero")
    }
}
