package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*object LegacyNameplateFacing {
    private const val FRONT_VIEW = 2

    private var frontView = false
    private var vanillaPitch = 0f

    @JvmStatic
    fun record(pitch: Float, perspective: Int) {
        vanillaPitch = pitch
        frontView = perspective == FRONT_VIEW
    }

    @JvmStatic
    fun leaveWorldView() {
        frontView = false
    }

    @JvmStatic
    fun pitch(pitch: Float): Float = if (frontView && pitch == vanillaPitch) -pitch else pitch
}
*///?}
