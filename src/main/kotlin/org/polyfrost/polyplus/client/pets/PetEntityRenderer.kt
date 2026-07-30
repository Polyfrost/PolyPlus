//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.pets

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.bedrock.model.BedrockStandaloneModel
import org.polyfrost.polyplus.client.bedrock.playback.AnimationSampler
import org.polyfrost.polyplus.client.bedrock.playback.BedrockAnimationPlayback
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.cosmetics.PetDefinition
import java.util.concurrent.ConcurrentHashMap
//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.EntityRenderState
//?}
//? if >= 1.21.10 && < 26.1 {
/*import net.minecraft.client.renderer.state.CameraRenderState
*///?}
//? if >= 26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState
//?}
//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes
//?} else {
/*import net.minecraft.client.renderer.RenderType
*///?}

private val FALLBACK_TEXTURE = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/pets/missing.png")

private const val TICKS_PER_TEXTURE_FRAME = 4

private fun sampleState(
    entity: PetEntity,
    definition: PetDefinition,
    stateName: String,
    partialTicks: Float,
    molangVariables: MutableMap<String, Float>,
): Map<String, BoneTransform> {
    val animationName = definition.stateMap[stateName] ?: definition.stateMap.values.firstOrNull()
    val animation = animationName?.let { definition.animations[it] } ?: return emptyMap()
    val elapsedTicks = entity.tickCount + partialTicks
    val timeTicks = BedrockAnimationPlayback.resolveTimeTicks(animation, elapsedTicks)
    return AnimationSampler.sample(animation, timeTicks, null, molangVariables)
}

private fun samplePose(
    entity: PetEntity,
    definition: PetDefinition,
    partialTicks: Float,
    molangVariables: MutableMap<String, Float>,
): PetPose {
    val runner = entity.controllerRunner
    if (runner != null) {
        return PetPose(runner.sample(partialTicks / BedrockConstants.TICKS_PER_SECOND, molangVariables), emptyMap(), 1f)
    }

    val blend = ((entity.animationBlendTicks + partialTicks) / PetEntity.ANIMATION_BLEND_TICKS).coerceIn(0f, 1f)
    val current = sampleState(entity, definition, entity.animationState, partialTicks, molangVariables)
    if (blend >= 1f || entity.previousAnimationState == entity.animationState) {
        return PetPose(current, emptyMap(), 1f)
    }
    val previous = sampleState(entity, definition, entity.previousAnimationState, partialTicks, molangVariables)
    return PetPose(previous, current, blend)
}

class PetPose(val from: Map<String, BoneTransform>, val to: Map<String, BoneTransform>, val blend: Float)

//? if >= 1.21.10 {
class PetRenderState : EntityRenderState() {
    var definition: PetDefinition? = null
    var pose: PetPose? = null
    var bodyYaw: Float = 0f
    var textureFrame: Int = 0
    var packedLight: Int = 0xF000F0
}

class PetEntityRenderer(context: EntityRendererProvider.Context) : EntityRenderer<PetEntity, PetRenderState>(context) {
    private val modelCache = ConcurrentHashMap<Int, BedrockStandaloneModel>()
    private val molangVariables = mutableMapOf<String, Float>()

    override fun createRenderState(): PetRenderState = PetRenderState()

    override fun shouldRender(
        entity: PetEntity,
        frustum: Frustum,
        camX: Double,
        camY: Double,
        camZ: Double,
    ): Boolean {
        val mc = Minecraft.getInstance()
        val isOwnPet = entity.ownerUuid == mc.player?.uuid
        if (mc.options.cameraType.isFirstPerson && isOwnPet) {
            return false
        }
        if (!super.shouldRender(entity, frustum, camX, camY, camZ)) {
            return false
        }
        if (!isOwnPet) {
            val viewer = mc.player
            val owner = entity.ownerEntity()
            if (viewer == null || owner == null || !viewer.hasLineOfSight(owner)) {
                return false
            }
        }
        return true
    }

    override fun extractRenderState(entity: PetEntity, state: PetRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        val definition = entity.definition
        state.definition = definition
        state.bodyYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.yRot)
        state.pose = if (definition != null) samplePose(entity, definition, partialTick, molangVariables) else null
        val frameCount = definition?.textureFrameCount ?: 1
        state.textureFrame = if (frameCount > 1) (entity.tickCount / TICKS_PER_TEXTURE_FRAME) % frameCount else 0
        state.packedLight = getPackedLightCoords(entity, partialTick)
    }

    override fun shouldShowName(entity: PetEntity, distance: Double): Boolean = false

    override fun submit(
        state: PetRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState,
    ) {
        val definition = state.definition ?: return
        val pose = state.pose ?: return
        val model = modelCache.getOrPut(definition.id) { BedrockStandaloneModel.build(definition.geometry) }

        poseStack.pushPose()
        poseStack.scale(-definition.scale, -definition.scale, definition.scale)
        poseStack.mulPose(Axis.YP.rotationDegrees(180f + state.bodyYaw))

        model.resetPose()
        if (pose.to.isEmpty()) {
            model.applyPose(pose.from, 1f)
        } else {
            model.applyPose(pose.from, 1f - pose.blend)
            model.applyPose(pose.to, pose.blend)
        }

        val frameCount = definition.textureFrameCount
        val vScale = 1f / frameCount
        val vOffset = state.textureFrame.toFloat() / frameCount
        val lightCoords = state.packedLight

        //? if >= 26.1 {
        val renderType = RenderTypes.entityCutout(definition.texture)
        //?} elif >= 1.21.11 {
        /*val renderType = RenderTypes.entityCutoutNoCull(definition.texture)
        *///?} else {
        /*val renderType = RenderType.entityCutoutNoCull(definition.texture)
        *///?}

        submitNodeCollector.submitCustomGeometry(poseStack, renderType) { basePose, buffer ->
            val localStack = PoseStack()
            localStack.last().set(basePose)
            for (root in model.roots) {
                root.render(localStack, buffer, lightCoords, OverlayTexture.NO_OVERLAY, vScale = vScale, vOffset = vOffset)
            }
        }

        poseStack.popPose()
    }
}
//?} elif >= 1.21.4 {
/*
class PetRenderState : net.minecraft.client.renderer.entity.state.EntityRenderState() {
    var definition: PetDefinition? = null
    var pose: PetPose? = null
    var bodyYaw: Float = 0f
    var textureFrame: Int = 0
}

class PetEntityRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<PetEntity, PetRenderState>(context) {
    private val modelCache = ConcurrentHashMap<Int, BedrockStandaloneModel>()
    private val molangVariables = mutableMapOf<String, Float>()

    override fun createRenderState(): PetRenderState = PetRenderState()

    override fun shouldRender(
        entity: PetEntity,
        frustum: Frustum,
        camX: Double,
        camY: Double,
        camZ: Double,
    ): Boolean {
        val mc = Minecraft.getInstance()
        val isOwnPet = entity.ownerUuid == mc.player?.uuid
        if (mc.options.cameraType.isFirstPerson && isOwnPet) {
            return false
        }
        if (!super.shouldRender(entity, frustum, camX, camY, camZ)) {
            return false
        }
        if (!isOwnPet) {
            val viewer = mc.player
            val owner = entity.ownerEntity()
            if (viewer == null || owner == null || !viewer.hasLineOfSight(owner)) {
                return false
            }
        }
        return true
    }

    override fun extractRenderState(entity: PetEntity, state: PetRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        val definition = entity.definition
        state.definition = definition
        state.bodyYaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yRotO, entity.yRot)
        state.pose = if (definition != null) samplePose(entity, definition, partialTick, molangVariables) else null
        val frameCount = definition?.textureFrameCount ?: 1
        state.textureFrame = if (frameCount > 1) (entity.tickCount / TICKS_PER_TEXTURE_FRAME) % frameCount else 0
    }

    override fun shouldShowName(entity: PetEntity, distance: Double): Boolean = false

    override fun render(
        state: PetRenderState,
        poseStack: PoseStack,
        buffer: net.minecraft.client.renderer.MultiBufferSource,
        packedLight: Int,
    ) {
        val definition = state.definition ?: return
        val pose = state.pose ?: return
        val model = modelCache.getOrPut(definition.id) { BedrockStandaloneModel.build(definition.geometry) }

        poseStack.pushPose()
        poseStack.scale(-definition.scale, -definition.scale, definition.scale)
        poseStack.mulPose(Axis.YP.rotationDegrees(180f + state.bodyYaw))

        model.resetPose()
        if (pose.to.isEmpty()) {
            model.applyPose(pose.from, 1f)
        } else {
            model.applyPose(pose.from, 1f - pose.blend)
            model.applyPose(pose.to, pose.blend)
        }

        val frameCount = definition.textureFrameCount
        val vScale = 1f / frameCount
        val vOffset = state.textureFrame.toFloat() / frameCount

        val vertexConsumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(definition.texture))
        for (root in model.roots) {
            root.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, vScale = vScale, vOffset = vOffset)
        }

        poseStack.popPose()
    }
}
*///?} else {
/*
class PetEntityRenderer(context: EntityRendererProvider.Context) :
    net.minecraft.client.renderer.entity.EntityRenderer<PetEntity>(context) {
    private val modelCache = ConcurrentHashMap<Int, BedrockStandaloneModel>()
    private val molangVariables = mutableMapOf<String, Float>()

    override fun getTextureLocation(entity: PetEntity): Identifier =
        entity.definition?.texture ?: FALLBACK_TEXTURE

    override fun shouldShowName(entity: PetEntity): Boolean = false

    override fun render(
        entity: PetEntity,
        entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: net.minecraft.client.renderer.MultiBufferSource,
        packedLight: Int,
    ) {
        val definition = entity.definition ?: return
        val model = modelCache.getOrPut(definition.id) { BedrockStandaloneModel.build(definition.geometry) }

        poseStack.pushPose()
        poseStack.scale(-definition.scale, -definition.scale, definition.scale)
        poseStack.mulPose(Axis.YP.rotationDegrees(180f + entityYaw))

        model.resetPose()
        val pose = samplePose(entity, definition, partialTicks, molangVariables)
        if (pose.to.isEmpty()) {
            model.applyPose(pose.from, 1f)
        } else {
            model.applyPose(pose.from, 1f - pose.blend)
            model.applyPose(pose.to, pose.blend)
        }

        val frameCount = definition.textureFrameCount
        val vScale = 1f / frameCount
        val vOffset = if (frameCount > 1) ((entity.tickCount / TICKS_PER_TEXTURE_FRAME) % frameCount).toFloat() / frameCount else 0f

        val vertexConsumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(definition.texture))
        for (root in model.roots) {
            root.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, vScale = vScale, vOffset = vOffset)
        }

        poseStack.popPose()
    }
}
*///?}
//?}
