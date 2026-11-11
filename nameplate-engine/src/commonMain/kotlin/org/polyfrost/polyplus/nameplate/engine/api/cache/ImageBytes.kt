package org.polyfrost.polyplus.nameplate.engine.api.cache

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage

const val DEFAULT_CACHE_BYTES = 16L * 1024 * 1024

internal const val IMAGE_OVERHEAD_BYTES = 64

fun imageBytes(image: PixelImage): Int =
    IMAGE_OVERHEAD_BYTES + image.rectCount * PixelImage.STRIDE * Int.SIZE_BYTES + image.stripBytes
