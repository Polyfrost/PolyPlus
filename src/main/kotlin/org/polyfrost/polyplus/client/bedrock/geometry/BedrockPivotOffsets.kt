package org.polyfrost.polyplus.client.bedrock.geometry

import org.joml.Vector3f

fun bedrockPivotOffset(pivot: Vector3f, reference: Vector3f): Vector3f = Vector3f(
    pivot.x - reference.x,
    reference.y - pivot.y,
    pivot.z - reference.z,
)

fun Vector3f.bedrockRotationRadians(): Vector3f = Vector3f(
    Math.toRadians(x.toDouble()).toFloat(),
    Math.toRadians(y.toDouble()).toFloat(),
    Math.toRadians(z.toDouble()).toFloat(),
)

fun BedrockBone.initialEffectPosition(
    bones: Map<String, BedrockBone>,
    playerGeometry: BedrockGeometry,
): Vector3f {
    PlayerModelBone.fromBedrockNameOrNull(parent)?.let { attachBone ->
        val playerPivot = playerGeometry.bones[attachBone.serializedName]?.pivot ?: Vector3f()
        return bedrockPivotOffset(pivot, playerPivot)
    }

    bones[parent]?.let { parentBone ->
        return bedrockPivotOffset(pivot, parentBone.pivot)
    }

    return PlayerModelBone.toModelOffset(pivot)
}

fun BedrockBone.initialEffectRotation(): Vector3f = rotation.bedrockRotationRadians()
