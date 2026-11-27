package org.polyfrost.polyplus.client.emotes.playback

import org.joml.Matrix4f

object EmoteLayerPose {
    private val pose = Matrix4f()
    private var held = false

    @JvmStatic
    fun hold(matrix: Matrix4f) {
        pose.set(matrix)
        held = true
    }

    @JvmStatic
    fun release() {
        held = false
    }

    internal fun held(): Matrix4f? = if (held) pose else null
}
