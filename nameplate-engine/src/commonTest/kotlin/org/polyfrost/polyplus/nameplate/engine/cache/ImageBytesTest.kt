package org.polyfrost.polyplus.nameplate.engine.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.cache.DEFAULT_CACHE_BYTES
import org.polyfrost.polyplus.nameplate.engine.api.cache.IMAGE_OVERHEAD_BYTES
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas

class ImageBytesTest {
    @Test
    fun emptyImageCostsOnlyTheFixedOverhead() {
        assertEquals(IMAGE_OVERHEAD_BYTES, imageBytes(PixelImage.EMPTY))
    }

    @Test
    fun imageCostGrowsWithRectCount() {
        val one = image(1)
        val three = image(3)
        assertEquals(1, one.rectCount)
        assertEquals(3, three.rectCount)
        assertEquals(IMAGE_OVERHEAD_BYTES + 1 * PixelImage.STRIDE * Int.SIZE_BYTES, imageBytes(one))
        assertEquals(IMAGE_OVERHEAD_BYTES + 3 * PixelImage.STRIDE * Int.SIZE_BYTES, imageBytes(three))
    }

    @Test
    fun defaultBudgetIsSixteenMegabytes() {
        assertEquals(16L * 1024 * 1024, DEFAULT_CACHE_BYTES)
        assertTrue(DEFAULT_CACHE_BYTES / imageBytes(image(50)) > 1000)
    }

    private fun image(rects: Int): PixelImage {
        val canvas = PixelGrid().canvas()
        for (i in 0 until rects) {
            canvas.fill(PixelRect(i * 2, 0, i * 2 + 1, 1), COLOR)
        }

        return canvas.compile()
    }

    private companion object {
        const val COLOR: Int = 0x7F112233
    }
}
