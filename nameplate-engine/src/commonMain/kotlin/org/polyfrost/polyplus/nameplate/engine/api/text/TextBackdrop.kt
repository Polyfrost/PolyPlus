package org.polyfrost.polyplus.nameplate.engine.api.text

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette

fun interface TextBackdrop {
    fun sample(width: Int, height: Int, x0: Int, y0: Int, x1: Int, y1: Int, sink: (Int) -> Unit)

    fun interface Frames {
        fun image(width: Int, height: Int, frame: Int): PixelImage
    }

    companion object {
        val NONE: TextBackdrop = TextBackdrop { _, _, _, _, _, _, _ -> }

        fun loop(frames: Int, images: Frames, palette: ResolvedPalette?): TextBackdrop {
            require(frames >= 1) { "A loop needs at least one frame" }

            return TextBackdrop { width, height, x0, y0, x1, y1, sink ->
                for (frame in 0 until frames) {
                    val image = images.image(width, height, frame)
                    if (palette == null) {
                        image.sample(x0, y0, x1, y1, sink)
                    } else {
                        image.sample(palette, x0, y0, x1, y1, sink)
                    }
                }
            }
        }
    }
}
