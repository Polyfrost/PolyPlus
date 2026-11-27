package org.polyfrost.polyplus.client.nameplate.render

object FrontViewPitch {
    fun facing(cached: Float, cameraPitch: Float): Float {
        if (cameraPitch == 0f || cached == 0f) return -cached
        return if ((cached > 0f) == (cameraPitch > 0f)) -cached else cached
    }
}
