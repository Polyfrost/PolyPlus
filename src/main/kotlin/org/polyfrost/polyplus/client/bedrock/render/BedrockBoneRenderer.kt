//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.bedrock.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.joml.Quaternionf
import org.joml.Vector3f

class BedrockBoneRenderer internal constructor(
    val name: String,
    val mesh: BedrockMesh,
    val children: List<BedrockBoneRenderer>,
    private val initialPosition: Vector3f,
    private val initialRotation: Vector3f,
) {
    var x = initialPosition.x
    var y = initialPosition.y
    var z = initialPosition.z
    var xRot = initialRotation.x
    var yRot = initialRotation.y
    var zRot = initialRotation.z
    var xScale = 1f
    var yScale = 1f
    var zScale = 1f

    fun resetPose() {
        x = initialPosition.x
        y = initialPosition.y
        z = initialPosition.z
        xRot = initialRotation.x
        yRot = initialRotation.y
        zRot = initialRotation.z
        xScale = 1f
        yScale = 1f
        zScale = 1f
    }

    fun applyTransform(transform: BoneTransform, weight: Float) {
        if (weight <= 0f)
            return

        x += transform.position.x * weight
        y -= transform.position.y * weight
        z += transform.position.z * weight

        xRot += transform.rotation.x * weight
        yRot += transform.rotation.y * weight
        zRot += transform.rotation.z * weight

        if (transform.scale.x != 1f || transform.scale.y != 1f || transform.scale.z != 1f) {
            val scaleBlend = 1f + (transform.scale.x - 1f) * weight
            xScale *= scaleBlend
            yScale *= scaleBlend
            zScale *= scaleBlend
        }
    }

    fun translateAndRotate(poseStack: PoseStack) {
        poseStack.translate(x / BedrockQuad.PIXEL_SCALE, y / BedrockQuad.PIXEL_SCALE, z / BedrockQuad.PIXEL_SCALE)
        if (xRot != 0f || yRot != 0f || zRot != 0f) {
            poseStack.mulPose(Quaternionf().rotationZYX(zRot, yRot, xRot))
        }
        if (xScale != 1f || yScale != 1f || zScale != 1f) {
            poseStack.scale(xScale, yScale, zScale)
        }
    }

    fun render(
        poseStack: PoseStack,
        buffer: VertexConsumer,
        lightCoords: Int,
        overlayCoords: Int,
        color: Int = -1,
        vScale: Float = 1f,
        vOffset: Float = 0f,
    ) {
        poseStack.pushPose()
        translateAndRotate(poseStack)
        mesh.render(poseStack.last(), buffer, lightCoords, overlayCoords, color, vScale, vOffset)

        for (child in children) {
            child.render(poseStack, buffer, lightCoords, overlayCoords, color, vScale, vOffset)
        }

        poseStack.popPose()
    }

    companion object {
        fun applyPose(
            bonesByName: Map<String, BedrockBoneRenderer>,
            boneNames: Set<String>,
            sample: Map<String, BoneTransform>,
            weight: Float,
        ) {
            if (weight <= 0f)
                return

            for (boneName in boneNames) {
                val bone = bonesByName[boneName] ?: continue
                val transform = sample[boneName] ?: continue
                bone.applyTransform(transform, weight)
            }
        }
    }
}
//?}
