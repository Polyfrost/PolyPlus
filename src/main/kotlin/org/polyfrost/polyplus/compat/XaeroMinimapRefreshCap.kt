package org.polyfrost.polyplus.compat

//? if xaerominimap {
import org.polyfrost.polyplus.client.PolyPlusConfig

class XaeroMinimapRefreshCap {
    private var nextRender = 0L
    private var lastKey = 0

    fun reuse(key: Int): Boolean {
        if (!PolyPlusConfig.xaeroMinimapRefreshCap) return false
        val now = System.nanoTime()
        if (key == lastKey && now - nextRender < 0) return true
        nextRender = if (key == lastKey && now - nextRender < FRAME_NANOS) nextRender + FRAME_NANOS else now + FRAME_NANOS
        lastKey = key
        return false
    }

    private companion object {
        const val FRAME_NANOS = 1_000_000_000L / 60
    }
}
//?}
