package org.polyfrost.polyplus.compat

//? if xaerominimap {
import org.polyfrost.polyplus.client.PolyPlusConfig

class XaeroMinimapRefreshCap {
    private var lastRender = 0L
    private var lastKey = 0

    fun reuse(key: Int): Boolean {
        if (!PolyPlusConfig.xaeroMinimapRefreshCap) return false
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastRender < FRAME_MILLIS) return true
        lastKey = key
        lastRender = now
        return false
    }

    private companion object {
        const val FRAME_MILLIS = 16L
    }
}
//?}
