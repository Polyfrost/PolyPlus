package org.polyfrost.polyplus.client.bedrock.render

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.model.BedrockEffectModel
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack

//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes
//?}

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
//?}

//? if < 1.21.11 && > 1.8.9 {
/*import net.minecraft.client.renderer.RenderType
*///?}

//? if < 1.21.10 && > 1.8.9 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}

//? if = 1.8.9
//import org.polyfrost.polyplus.client.render.VertexConsumer

object BedrockAttachedModelRenderer {
    data class DrawCall(
        val model: BedrockEffectModel,
        val texture: Identifier,
        val sample: Map<String, BoneTransform>,
        val poseWeight: Float,
    )

    /** Two-sided, as cosmetics are modelled. */
    //? if >= 26.1 {
    fun renderType(texture: Identifier, translucent: Boolean) =
        if (translucent) RenderTypes.entityTranslucent(texture) else RenderTypes.entityCutout(texture)
    //?} elif >= 1.21.11 {
    /*fun renderType(texture: Identifier, translucent: Boolean) =
        if (translucent) RenderTypes.entityTranslucent(texture) else RenderTypes.entityCutoutNoCull(texture)
    *///?} elif > 1.8.9 {
    /*fun renderType(texture: Identifier, translucent: Boolean) =
        if (translucent) RenderType.entityTranslucent(texture) else RenderType.entityCutoutNoCull(texture)
    *///?}

    private fun prepare(draw: DrawCall) {
        draw.model.resetPose()
        if (draw.sample.isNotEmpty()) {
            draw.model.applyPose(draw.sample, draw.poseWeight)
        }
    }

    //? if >= 1.21.10 {
    fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        overlayCoords: Int,
        playerModel: PlayerModel,
        draws: Iterable<DrawCall>,
    ) {
        for (draw in draws) {
            prepare(draw)

            val renderType = renderType(draw.texture, translucent = false)

            for (attachment in draw.model.attachments) {
                poseStack.pushPose()
                attachment.attachBone.translateAndRotateChain(playerModel, poseStack)

                submitNodeCollector.submitCustomGeometry(poseStack, renderType) { basePose, buffer ->
                    val localStack = PoseStack()
                    localStack.last().set(basePose)
                    attachment.rootBone.render(localStack, buffer, lightCoords, overlayCoords)
                }

                poseStack.popPose()
            }
        }
    }
    //?} elif > 1.8.9 {
    /*fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        overlayCoords: Int,
        playerModel: PlayerModel,
        draws: Iterable<DrawCall>,
    ) {
        for (draw in draws) {
            prepare(draw)
            val buffer = bufferSource.getBuffer(renderType(draw.texture, translucent = false))

            for (attachment in draw.model.attachments) {
                poseStack.pushPose()
                attachment.attachBone.translateAndRotateChain(playerModel, poseStack)
                attachment.rootBone.render(poseStack, buffer, lightCoords, overlayCoords)
                poseStack.popPose()
            }
        }
    }
    *///?} else {
    /*fun render(
        poseStack: PoseStack,
        lightCoords: Int,
        overlayCoords: Int,
        playerModel: PlayerModel,
        draws: Iterable<DrawCall>,
    ) {
        for (draw in draws) {
            prepare(draw)
            VertexConsumer.draw(draw.texture, translucent = false) { buffer ->
                for (attachment in draw.model.attachments) {
                    poseStack.pushPose()
                    attachment.attachBone.translateAndRotateChain(playerModel, poseStack)
                    attachment.rootBone.render(poseStack, buffer, lightCoords, overlayCoords)
                    poseStack.popPose()
                }
            }
        }
    }
    *///?}
}
