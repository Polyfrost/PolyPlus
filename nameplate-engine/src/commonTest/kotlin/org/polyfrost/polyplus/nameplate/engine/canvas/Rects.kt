package org.polyfrost.polyplus.nameplate.engine.canvas

import kotlin.test.assertNull
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.support.pixelKey

internal object Rects {
    fun pixels(rects: IntArray): Map<Long, Int> {
        val pixels = HashMap<Long, Int>()
        for (i in rects.indices step PixelImage.STRIDE) {
            for (y in rects[i + 1] until rects[i + 3]) {
                for (x in rects[i] until rects[i + 2]) {
                    val previous = pixels.put(pixelKey(x, y), rects[i + 4])
                    assertNull(previous, "pixel $x,$y emitted twice")
                }
            }
        }

        return pixels
    }
}
