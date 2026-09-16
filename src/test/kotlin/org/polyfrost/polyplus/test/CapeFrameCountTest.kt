package org.polyfrost.polyplus.test

import com.mojang.blaze3d.platform.NativeImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.assets.detectVerticalTextureFrameCount
import org.polyfrost.polyplus.client.cosmetics.capeFrameIndex
import org.polyfrost.polyplus.client.cosmetics.capeFrameOffset
import org.polyfrost.polyplus.client.cosmetics.capeMillisPerFrameFromName
import org.polyfrost.polyplus.client.cosmetics.capeFrameWithinBudget
import org.polyfrost.polyplus.client.utils.copyFrameInto
import java.awt.image.BufferedImage

class CapeFrameCountTest {
    @Test
    fun `a whole-number stack of the static cape is a sheet`() {
        assertEquals(8, detectVerticalTextureFrameCount(64, 32, 64, 256, 0f, minFrames = 2))
        assertEquals(2, detectVerticalTextureFrameCount(64, 32, 64, 64, 0f, minFrames = 2), "two frames is a legitimate cape")
        assertEquals(16, detectVerticalTextureFrameCount(512, 256, 512, 4096, 0f, minFrames = 2), "HD capes stack the same way")
    }

    @Test
    fun `a sheet that is not a clean stack falls back to the static cape`() {
        assertEquals(1, detectVerticalTextureFrameCount(64, 32, 64, 200, 0f, minFrames = 2), "a ragged multiple is not a stack")
        assertEquals(1, detectVerticalTextureFrameCount(64, 32, 128, 256, 0f, minFrames = 2), "a different width is not a stack")
        assertEquals(1, detectVerticalTextureFrameCount(64, 0, 64, 256, 0f, minFrames = 2), "a zero-height cape cannot be divided into")
        assertEquals(1, detectVerticalTextureFrameCount(0, 32, 0, 256, 0f, minFrames = 2), "a zero-width cape is not a stack")
    }

    @Test
    fun `a sheet that is just the static cape is one frame`() {
        assertEquals(1, detectVerticalTextureFrameCount(64, 32, 64, 32, 0f, minFrames = 2))
        assertEquals(1, detectVerticalTextureFrameCount(64, 32, 64, 0, 0f, minFrames = 2), "an empty sheet is one frame, never zero")
    }

    @Test
    fun `the frame index advances once per frame and wraps`() {
        assertEquals(0, capeFrameIndex(0L, 4))
        assertEquals(0, capeFrameIndex(99L, 4), "a frame holds for 100ms")
        assertEquals(1, capeFrameIndex(100L, 4))
        assertEquals(3, capeFrameIndex(399L, 4))
        assertEquals(0, capeFrameIndex(400L, 4), "past the last frame it wraps to the first")
        assertEquals(1, capeFrameIndex(500L, 4))
    }

    @Test
    fun `the frame index stays in range for degenerate clocks and frame counts`() {
        assertEquals(3, capeFrameIndex(-100L, 4), "a monotonic clock may start negative")
        assertEquals(0, capeFrameIndex(-400L, 4))
        assertEquals(3, capeFrameIndex(-1L, 4), "the frame straddling zero is not 200ms long")
        assertEquals(2, capeFrameIndex(-101L, 4), "a negative non-multiple floors, it does not truncate")
        assertEquals(0, capeFrameIndex(12345L, 1), "a static cape never leaves frame 0")
        assertEquals(0, capeFrameIndex(12345L, 0), "a frameless cape never divides by zero")
    }

    @Test
    fun `a sheet names its own pace`() {
        assertEquals(80L, capeMillisPerFrameFromName("cape.80ms.sheet"))
        assertEquals(80L, capeMillisPerFrameFromName("cape.80MS.SHEET"), "extensions are matched case-insensitively")
        assertEquals(100L, capeMillisPerFrameFromName("cape.sheet"), "a sheet with no pace keeps the default")
        assertEquals(100L, capeMillisPerFrameFromName("300ms-test.sheet"), "the pace is only read from its own segment")
        assertEquals(100L, capeMillisPerFrameFromName("cape.80ms.png"), "only sheets carry a pace")
    }

    @Test
    fun `a pace outside the sane range falls back to the default`() {
        assertEquals(50L, capeMillisPerFrameFromName("cape.50ms.sheet"), "the fastest pace is allowed")
        assertEquals(5000L, capeMillisPerFrameFromName("cape.5000ms.sheet"), "the slowest pace is allowed")
        assertEquals(100L, capeMillisPerFrameFromName("cape.49ms.sheet"), "faster than one frame per tick")
        assertEquals(100L, capeMillisPerFrameFromName("cape.5001ms.sheet"), "slower than an animation")
        assertEquals(100L, capeMillisPerFrameFromName("cape.0ms.sheet"), "never divide by zero")
        assertEquals(100L, capeMillisPerFrameFromName("cape.9999999ms.sheet"), "a pace too long to be a mistake")
    }

    @Test
    fun `a pace of zero cannot divide by zero`() {
        assertEquals(0, capeFrameIndex(0L, 4, 0L))
        assertEquals(3, capeFrameIndex(3L, 4, 0L), "a zero pace degrades to one frame per millisecond")
        assertEquals(0, capeFrameIndex(12L, 4, -5L), "and so does a negative one")
    }

    @Test
    fun `a named pace drives the frame index`() {
        assertEquals(0, capeFrameIndex(199L, 4, 200L))
        assertEquals(1, capeFrameIndex(200L, 4, 200L))
        assertEquals(0, capeFrameIndex(800L, 4, 200L), "it still wraps")
        assertEquals(3, capeFrameIndex(-200L, 4, 200L), "and still tolerates a negative origin")
    }

    @Test
    fun `copyFrameInto lifts each frame out of the sheet at its own offset`() {
        val frameHeight = 2
        val sheet = BufferedImage(2, frameHeight * 3, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until sheet.height) {
            for (x in 0 until 2) sheet.setRGB(x, y, pixel(x, y))
        }

        for (frame in 0 until 3) {
            NativeImage(2, frameHeight, true).use { target ->
                sheet.copyFrameInto(target, frame * frameHeight)
                for (y in 0 until frameHeight) {
                    for (x in 0 until 2) {
                        assertEquals(
                            pixel(x, frame * frameHeight + y),
                            target.argbAt(x, y),
                            "pixel $x,$y of frame $frame",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `copyFrameInto fills a reused buffer with each frame it is handed`() {
        val frameHeight = 2
        val sheet = BufferedImage(2, frameHeight * 3, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until sheet.height) {
            for (x in 0 until 2) sheet.setRGB(x, y, pixel(x, y))
        }
        val buffer = IntArray(2 * frameHeight)

        for (frame in 2 downTo 0) {
            NativeImage(2, frameHeight, true).use { target ->
                sheet.copyFrameInto(target, frame * frameHeight, buffer)
                for (y in 0 until frameHeight) {
                    for (x in 0 until 2) {
                        assertEquals(
                            pixel(x, frame * frameHeight + y),
                            target.argbAt(x, y),
                            "pixel $x,$y of frame $frame after reusing the buffer",
                        )
                    }
                }
            }
        }

        NativeImage(2, frameHeight, true).use { target ->
            assertThrows(IllegalArgumentException::class.java) {
                sheet.copyFrameInto(target, 0, IntArray(2 * frameHeight - 1))
            }
        }
    }

    @Test
    fun `copyFrameInto refuses a frame that does not fit the sheet`() {
        val sheet = BufferedImage(2, 4, BufferedImage.TYPE_INT_ARGB)
        NativeImage(2, 2, true).use { target ->
            assertThrows(IllegalArgumentException::class.java) { sheet.copyFrameInto(target, 4) }
            assertThrows(IllegalArgumentException::class.java) { sheet.copyFrameInto(target, -2) }
            assertThrows(IllegalArgumentException::class.java) { sheet.copyFrameInto(target, 3) }
        }
        NativeImage(3, 2, true).use { wider ->
            assertThrows(IllegalArgumentException::class.java) { sheet.copyFrameInto(wider, 0) }
        }
    }

    @Test
    fun `each frame starts its own height down the sheet`() {
        assertEquals(0, capeFrameOffset(32, 0))
        assertEquals(32, capeFrameOffset(32, 1))
        assertEquals(96, capeFrameOffset(32, 3), "the last frame starts one frame short of the end")
        assertEquals(6, capeFrameOffset(10 / 4, 3), "a ragged sheet still lands inside itself")
    }

    @Test
    fun `only the frame is budgeted, not the sheet`() {
        assertTrue(capeFrameWithinBudget(64, 32), "a vanilla cape")
        assertTrue(capeFrameWithinBudget(512, 256), "an HD cape")
        assertTrue(capeFrameWithinBudget(1024, 512), "exactly the budget still fits")
        assertFalse(capeFrameWithinBudget(1024, 513), "one row past the budget")
        assertFalse(capeFrameWithinBudget(4096, 2048), "too big to re-upload every frame")
        assertTrue(capeFrameWithinBudget(64, 65536 / 2048), "2048 frames of a vanilla cape is fine")
    }

    private fun pixel(x: Int, y: Int): Int = 0xFF000000.toInt() or (0x113300 * (y + 1)) or (x + 1)
}

private fun NativeImage.argbAt(x: Int, y: Int): Int {
    //? if >= 1.21.4
    return getPixel(x, y)
    //? if < 1.21.4
    //return getPixelRGBA(x, y).let { (it and 0xFF00FF00.toInt()) or ((it and 0x00FF0000) ushr 16) or ((it and 0x000000FF) shl 16) }
}
