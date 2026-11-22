package org.polyfrost.polyplus.nameplate.engine.support

import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.DEFAULT_CACHE_BYTES
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage

internal class CountingCache {
    var misses: Long = 0
        private set

    val cache: ByteLruCache<LayerKey, PixelImage> = ByteLruCache(DEFAULT_CACHE_BYTES) {
        misses++
        imageBytes(it)
    }

    val size: Int
        get() = misses.toInt()
}
