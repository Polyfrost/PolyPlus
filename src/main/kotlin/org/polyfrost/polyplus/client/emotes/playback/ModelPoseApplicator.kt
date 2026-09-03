package org.polyfrost.polyplus.client.emotes.playback

import net.minecraft.client.model.geom.ModelPart
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform

object ModelPoseApplicator {
    fun resetAnimatedPlayerBones(model: PlayerModel, animatedBoneNames: Iterable<String>) {
        for (boneName in animatedBoneNames) {
            val bone = PlayerModelBone.fromBedrockNameOrNull(boneName) ?: continue
            bone.resolve(model).resetPose()
            bone.overlayPart(model)?.resetPose()
        }
    }

    fun apply(
        model: PlayerModel,
        transforms: Map<String, BoneTransform>,
        weight: Float,
    ) {
        if (weight <= 0f) {
            return
        }

        val animatedBones = transforms.keys
        for ((boneName, transform) in transforms) {
            val bone = PlayerModelBone.fromBedrockNameOrNull(boneName) ?: continue

            val part = bone.resolve(model)
            applyTransform(part, bone, transform, weight)
            resetOverlayIfNeeded(model, bone, animatedBones)
        }
    }

    private fun applyTransform(
        part: ModelPart,
        bone: PlayerModelBone,
        transform: BoneTransform,
        weight: Float,
    ) {
        if (bone == PlayerModelBone.HEAD) {
            applyHeadTransform(part, transform, weight)
        } else {
            applyAdditiveTransform(part, transform, weight)
        }
    }

    private fun applyHeadTransform(
        part: ModelPart,
        transform: BoneTransform,
        weight: Float,
    ) {
        val targetX = transform.position.x
        val targetY = -transform.position.y
        val targetZ = transform.position.z

        part.x += (targetX - part.x) * weight
        part.y += (targetY - part.y) * weight
        part.z += (targetZ - part.z) * weight

        part.xRot += (transform.rotation.x - part.xRot) * weight
        part.yRot += (transform.rotation.y - part.yRot) * weight
        part.zRot += (transform.rotation.z - part.zRot) * weight

        val targetScale = transform.scale.x
        if (targetScale != 1f || transform.scale.y != 1f || transform.scale.z != 1f) {
            part.xScale += (targetScale - part.xScale) * weight
            part.yScale += (targetScale - part.yScale) * weight
            part.zScale += (targetScale - part.zScale) * weight
        }
    }

    private fun applyAdditiveTransform(
        part: ModelPart,
        transform: BoneTransform,
        weight: Float,
    ) {
        part.x += transform.position.x * weight
        part.y -= transform.position.y * weight
        part.z += transform.position.z * weight

        part.xRot += transform.rotation.x * weight
        part.yRot += transform.rotation.y * weight
        part.zRot += transform.rotation.z * weight

        if (transform.scale.x != 1f || transform.scale.y != 1f || transform.scale.z != 1f) {
            val scaleBlend = 1f + (transform.scale.x - 1f) * weight
            part.xScale *= scaleBlend
            part.yScale *= scaleBlend
            part.zScale *= scaleBlend
        }
    }

    private fun resetOverlayIfNeeded(model: PlayerModel, bone: PlayerModelBone, animatedBones: Set<String>) {
        val overlay = bone.overlayPart(model) ?: return
        if (bone.serializedName !in animatedBones) {
            overlay.resetPose()
        }
    }
}
