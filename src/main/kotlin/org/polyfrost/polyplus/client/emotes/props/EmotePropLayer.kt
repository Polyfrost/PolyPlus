package org.polyfrost.polyplus.client.emotes.props

//? if > 1.8.9 {
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import org.polyfrost.polyplus.client.bedrock.render.BedrockAttachedModelRenderer
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.emotes.playback.EmoteController
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?}

//? if >= 1.21.4 {
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess
//?}

//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState
*///?}

//? if < 1.21.10 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}

//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess
*///?}

//? if >= 1.21.10 {
class EmotePropLayer(renderer: RenderLayerParent<AvatarRenderState, PlayerModel>) :
    RenderLayer<AvatarRenderState, PlayerModel>(renderer) {

    override fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (state.isInvisible) return
        val draw = controllerOf(state)?.props() ?: return
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        submitNodeCollector.submitCustomGeometry(poseStack, BedrockAttachedModelRenderer.renderType(draw.texture, RemoteTextures.isTranslucent(draw.texture))) { pose, buffer ->
            EmoteProps.emit(draw, pose, buffer, lightCoords, overlay, null)
        }
    }
}
//?} elif >= 1.21.4 {
/*class EmotePropLayer(renderer: RenderLayerParent<PlayerRenderState, PlayerModel>) :
    RenderLayer<PlayerRenderState, PlayerModel>(renderer) {

    override fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        state: PlayerRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (state.isInvisible) return
        val draw = controllerOf(state)?.props() ?: return
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        val buffer = bufferSource.getBuffer(BedrockAttachedModelRenderer.renderType(draw.texture, RemoteTextures.isTranslucent(draw.texture)))
        EmoteProps.emit(draw, poseStack.last(), buffer, lightCoords, overlay, null)
    }
}
*///?} else {
/*class EmotePropLayer(renderer: RenderLayerParent<AbstractClientPlayer, PlayerModel>) :
    RenderLayer<AbstractClientPlayer, PlayerModel>(renderer) {

    private val correction = Matrix4f()

    override fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        player: AbstractClientPlayer,
        limbSwing: Float,
        limbSwingAmount: Float,
        partialTicks: Float,
        ageInTicks: Float,
        yRot: Float,
        xRot: Float,
    ) {
        if (player.isInvisible) return
        val controller = (player as PlayerEmotesAccess).`polyplus$emoteController`().takeIf { it.isActive } ?: return
        val draw = controller.props() ?: return
        val root = (parentModel as PlayerModelRootAccess).`polyplus$root`()
        correction.translation(root.x / PIXELS, root.y / PIXELS, root.z / PIXELS)
            .rotateZYX(root.zRot, root.yRot, root.xRot)
            .scale(root.xScale, root.yScale, root.zScale)
            .invert()
        val overlay = LivingEntityRenderer.getOverlayCoords(player, 0f)
        val buffer = bufferSource.getBuffer(BedrockAttachedModelRenderer.renderType(draw.texture, RemoteTextures.isTranslucent(draw.texture)))
        EmoteProps.emit(draw, poseStack.last(), buffer, lightCoords, overlay, correction)
    }

    private companion object {
        const val PIXELS = 16f
    }
}
*///?}

//? if >= 1.21.4 {
private fun controllerOf(state: Any): EmoteController? =
    (state as? AvatarEmoteRenderAccess)?.`polyplus$boundEmoteController`()?.takeIf { it.isActive }
//?}
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.entity.PlayerRenderer
import net.minecraft.client.render.entity.layer.EntityRenderLayer
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.cosmetics.access.ModelPartScaleAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.render.PoseStack
import org.polyfrost.polyplus.client.render.VertexConsumer
import org.polyfrost.polyplus.compat.OverflowAnimationsCompat

class EmotePropLayer(private val renderer: PlayerRenderer) : EntityRenderLayer<ClientPlayerEntity> {
    private val correction = Matrix4f()
    private val pose = PoseStack()

    override fun render(
        player: ClientPlayerEntity,
        walkAnimationProgress: Float,
        walkAnimationSpeed: Float,
        tickDelta: Float,
        bob: Float,
        yaw: Float,
        pitch: Float,
        scale: Float,
    ) {
        if (player.isInvisible) return
        val controller = (player as PlayerEmotesAccess).`polyplus$emoteController`().takeIf { it.isActive } ?: return
        val draw = controller.props() ?: return
        val model = renderer.getModel()
        val root = (model as PlayerModelRootAccess).`polyplus$root`()
        val sneak = if (model.sneaking && !OverflowAnimationsCompat.dropsSneakOffset()) SNEAK_OFFSET else 0f
        val rootScale = root as ModelPartScaleAccess
        val inverseRoot = Matrix4f().translation(root.x / PIXELS, root.y / PIXELS, root.z / PIXELS)
            .rotateZYX(root.rotationZ, root.rotationY, root.rotationX)
            .scale(rootScale.`polyplus$scaleX`(), rootScale.`polyplus$scaleY`(), rootScale.`polyplus$scaleZ`())
            .invert()
        correction.translation(0f, sneak, 0f).mul(inverseRoot)
        val light = if (player.isOnFire) FULL_BRIGHT else player.getLightLevel(tickDelta)
        VertexConsumer.draw(draw.texture, RemoteTextures.isTranslucent(draw.texture)) { buffer ->
            EmoteProps.emit(draw, pose.last(), buffer, light, 0, correction)
        }
    }

    override fun colorsWhenDamaged(): Boolean = true

    private companion object {
        const val PIXELS = 16f
        const val SNEAK_OFFSET = 0.2f
        const val FULL_BRIGHT = 0xF000F0
    }
}
*///?}
