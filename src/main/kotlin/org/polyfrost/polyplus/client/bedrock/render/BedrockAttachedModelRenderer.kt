//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.bedrock.render

import com.mojang.blaze3d.vertex.PoseStack
//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes
//?} else {
/*import net.minecraft.client.renderer.RenderType
*///?}
//? if >= 1.21.10
import net.minecraft.client.renderer.SubmitNodeCollector
//? if < 1.21.10
//import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.model.BedrockEffectModel
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel

object BedrockAttachedModelRenderer {
    data class DrawCall(
        val model: BedrockEffectModel,
        val texture: Identifier,
        val sample: Map<String, BoneTransform>,
        val poseWeight: Float,
    )

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

            //? if >= 26.1 {
            /*val renderType = RenderTypes.entityCutout(draw.texture)
            *///?} elif >= 1.21.11 {
            val renderType = RenderTypes.entityCutoutNoCull(draw.texture)
            //?} else {
            /*val renderType = RenderType.entityCutoutNoCull(draw.texture)
            *///?}

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
    //?} else {
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
            val renderType = RenderType.entityCutoutNoCull(draw.texture)
            val buffer = bufferSource.getBuffer(renderType)

            for (attachment in draw.model.attachments) {
                poseStack.pushPose()
                attachment.attachBone.translateAndRotateChain(playerModel, poseStack)
                attachment.rootBone.render(poseStack, buffer, lightCoords, overlayCoords)
                poseStack.popPose()
            }
        }
    }
    *///?}
}
//?}
