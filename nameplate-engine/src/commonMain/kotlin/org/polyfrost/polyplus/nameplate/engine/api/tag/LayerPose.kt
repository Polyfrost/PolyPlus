package org.polyfrost.polyplus.nameplate.engine.api.tag

class LayerPose(val dx: Int, val dy: Int, val opacity: Int, val visible: Boolean) {
    companion object {
        const val OPAQUE = 255

        internal val IDENTITY: LayerPose = LayerPose(0, 0, OPAQUE, true)
    }
}
