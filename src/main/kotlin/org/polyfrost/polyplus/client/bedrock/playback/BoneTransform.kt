package org.polyfrost.polyplus.client.bedrock.playback

import org.joml.Vector3f

data class BoneTransform(
    val position: Vector3f = Vector3f(),
    val rotation: Vector3f = Vector3f(),
    val scale: Vector3f = Vector3f(1f, 1f, 1f),
)
