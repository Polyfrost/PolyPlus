package org.polyfrost.polyplus.client.emotes.playback

//? if > 1.8.9 {
import net.minecraft.client.model.geom.ModelPart
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel

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
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.model.Model
import net.minecraft.client.render.model.ModelPart
import net.minecraft.client.render.model.entity.HumanoidModel
import net.minecraft.entity.Entity
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel

object ModelPoseApplicator {
    @JvmField
    var firstPersonHand = false

    private var posedModel: PlayerModel? = null
    private var posedEntity: Entity? = null

    @JvmStatic
    fun afterSetupAnimation(model: HumanoidModel, entity: Entity?, walkAnimationSpeed: Float, bob: Float) {
        if (model is PlayerModel) {
            posedModel = null
            posedEntity = null
            if (firstPersonHand) return
            val player = entity as? ClientPlayerEntity ?: return
            val controller = (player as PlayerEmotesAccess).`polyplus$emoteController`()
            if (!controller.isActive) return
            controller.applyToModel(model, PlayerRenderContext.from(player, 0f, walkAnimationSpeed, bob))
            (model as PlayerModelRootAccess).`polyplus$markPosed`()
            posedModel = model
            posedEntity = player
        } else if (entity != null && entity === posedEntity) {
            val source = posedModel ?: return
            for ((from, to) in listOf(
                source.head to model.head, source.hat to model.hat, source.body to model.body,
                source.rightArm to model.rightArm, source.leftArm to model.leftArm,
                source.rightLeg to model.rightLeg, source.leftLeg to model.leftLeg,
            )) {
                Model.copyRotation(from, to)
            }
            (model as PlayerModelRootAccess).`polyplus$markPosed`()
        }
    }

    private fun ModelPart.resetPose(model: PlayerModel) = (model as PlayerModelRootAccess).`polyplus$resetPart`(this)

    fun resetAnimatedPlayerBones(model: PlayerModel, animatedBoneNames: Iterable<String>) {
        for (boneName in animatedBoneNames) {
            val bone = PlayerModelBone.fromBedrockNameOrNull(boneName) ?: continue
            bone.resolve(model).resetPose(model)
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

        for ((boneName, transform) in transforms) {
            val bone = PlayerModelBone.fromBedrockNameOrNull(boneName) ?: continue
            applyTransform(bone.resolve(model), bone, transform, weight)
        }
        if (PlayerModelBone.HELMET.serializedName !in transforms.keys) {
            Model.copyRotation(model.head, model.hat)
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

        part.rotationX += (transform.rotation.x - part.rotationX) * weight
        part.rotationY += (transform.rotation.y - part.rotationY) * weight
        part.rotationZ += (transform.rotation.z - part.rotationZ) * weight
    }

    private fun applyAdditiveTransform(
        part: ModelPart,
        transform: BoneTransform,
        weight: Float,
    ) {
        part.x += transform.position.x * weight
        part.y -= transform.position.y * weight
        part.z += transform.position.z * weight

        part.rotationX += transform.rotation.x * weight
        part.rotationY += transform.rotation.y * weight
        part.rotationZ += transform.rotation.z * weight
    }
}
*///?}
