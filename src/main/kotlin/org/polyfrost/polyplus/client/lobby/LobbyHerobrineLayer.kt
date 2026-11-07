package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.client.renderer.entity.layers.EyesLayer
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack
//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}
//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState
*///?}
//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer
*///?}
//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
//?} else {
/*import net.minecraft.client.renderer.RenderType
*///?}

//? if >= 1.21.10 {
class LobbyHerobrineLayer(renderer: RenderLayerParent<AvatarRenderState, PlayerModel>) : EyesLayer<AvatarRenderState, PlayerModel>(renderer) {
    override fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (!LobbyHerobrine.shows(state.id)) return
        val body = LobbyHerobrine.bodyAlpha()
        val skin = LobbyHerobrine.skinTexture()
        if (state.isInvisible && body > 0f && skin != null) {
            //? if >= 26.3 {
            submitNodeCollector.submitModel(parentModel, state, poseStack, translucent(skin), lightCoords, OverlayTexture.NO_OVERLAY, tint(body), null, state.outlineColor)
            //?} else {
            /*submitNodeCollector.submitModel(parentModel, state, poseStack, translucent(skin), lightCoords, OverlayTexture.NO_OVERLAY, tint(body), null, state.outlineColor, null)
            *///?}
        }
        val eyes = LobbyHerobrine.eyesAlpha()
        if (eyes <= 0f) return
        //? if >= 26.3 {
        submitNodeCollector.order(1).submitModel(parentModel, state, poseStack, renderType(), lightCoords, OverlayTexture.NO_OVERLAY, glow(eyes), null, state.outlineColor)
        //?} else {
        /*submitNodeCollector.order(1).submitModel(parentModel, state, poseStack, renderType(), lightCoords, OverlayTexture.NO_OVERLAY, glow(eyes), null, state.outlineColor, null)
        *///?}
    }
//?} elif >= 1.21.4 {
/*class LobbyHerobrineLayer(renderer: RenderLayerParent<PlayerRenderState, PlayerModel>) : EyesLayer<PlayerRenderState, PlayerModel>(renderer) {
    override fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        state: PlayerRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (!LobbyHerobrine.shows(state.id)) return
        draw(poseStack, bufferSource, lightCoords, state.isInvisible)
    }
*///?} else {
/*class LobbyHerobrineLayer(renderer: RenderLayerParent<AbstractClientPlayer, PlayerModel>) : EyesLayer<AbstractClientPlayer, PlayerModel>(renderer) {
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
        if (!LobbyHerobrine.shows(player.id)) return
        draw(poseStack, bufferSource, lightCoords, player.isInvisible)
    }
*///?}

    //? if < 1.21.10 {
    /*private fun draw(poseStack: PoseStack, bufferSource: MultiBufferSource, lightCoords: Int, invisible: Boolean) {
        val body = LobbyHerobrine.bodyAlpha()
        val skin = LobbyHerobrine.skinTexture()
        if (invisible && body > 0f && skin != null) {
            parentModel.renderToBuffer(poseStack, bufferSource.getBuffer(translucent(skin)), lightCoords, OverlayTexture.NO_OVERLAY, tint(body))
        }
        val eyes = LobbyHerobrine.eyesAlpha()
        if (eyes > 0f) parentModel.renderToBuffer(poseStack, bufferSource.getBuffer(renderType()), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow(eyes))
    }
    *///?}

    private fun tint(alpha: Float): Int = (channel(alpha) shl 24) or 0xFFFFFF

    private fun glow(alpha: Float): Int {
        val level = channel(alpha)
        return (0xFF shl 24) or (level shl 16) or (level shl 8) or level
    }

    private fun channel(alpha: Float): Int = (alpha.coerceIn(0f, 1f) * 255f).toInt()

    private fun translucent(texture: Identifier): RenderType =
        //? if >= 1.21.11 {
        RenderTypes.entityTranslucent(texture)
        //?} else {
        /*RenderType.entityTranslucent(texture)
        *///?}

    override fun renderType(): RenderType = EYES

    private companion object {
        const val FULL_BRIGHT = 0xF000F0

        //? if >= 1.21.11 {
        val EYES: RenderType by lazy { RenderTypes.eyes(LobbyHerobrine.EYES) }
        //?} else {
        /*val EYES: RenderType by lazy { RenderType.eyes(LobbyHerobrine.EYES) }
        *///?}
    }
}
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.entity.PlayerRenderer
import net.minecraft.client.render.entity.layer.EntityRenderLayer
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import org.lwjgl.opengl.GL11

class LobbyHerobrineLayer(private val renderer: PlayerRenderer) : EntityRenderLayer<ClientPlayerEntity> {
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
        if (!LobbyHerobrine.shows(player.id)) return
        val body = LobbyHerobrine.bodyAlpha()
        val skin = LobbyHerobrine.skinTexture()
        if (player.isInvisible && body > 0f && skin != null) {
            renderer.bindTexture(skin)
            GlStateManager.enableBlend()
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GlStateManager.alphaFunc(GL11.GL_GREATER, MIN_ALPHA)
            GlStateManager.depthMask(false)
            GlStateManager.color4f(1f, 1f, 1f, body)
            renderer.getModel().render(player, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale)
            GlStateManager.alphaFunc(GL11.GL_GREATER, DEFAULT_ALPHA)
            GlStateManager.depthMask(true)
            GlStateManager.disableBlend()
        }
        val eyes = LobbyHerobrine.eyesAlpha()
        if (eyes <= 0f) {
            GlStateManager.color4f(1f, 1f, 1f, 1f)
            return
        }
        renderer.bindTexture(LobbyHerobrine.EYES)
        GlStateManager.enableBlend()
        GlStateManager.disableAlphaTest()
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE)
        GlStateManager.depthMask(!player.isInvisible)
        lightmap(FULL_BRIGHT)
        GlStateManager.color4f(eyes, eyes, eyes, 1f)
        renderer.getModel().render(player, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale)
        lightmap(player.getLightLevel(tickDelta))
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        GlStateManager.depthMask(true)
        GlStateManager.disableBlend()
        GlStateManager.enableAlphaTest()
    }

    override fun colorsWhenDamaged(): Boolean = false

    private fun lightmap(light: Int) = GLX.multiTexCoord2f(GLX.GL_TEXTURE1, (light % LIGHT_SPLIT).toFloat(), (light / LIGHT_SPLIT).toFloat())

    private companion object {
        const val FULL_BRIGHT = 0xF000F0
        const val LIGHT_SPLIT = 65536
        const val MIN_ALPHA = 0.003921569f
        const val DEFAULT_ALPHA = 0.1f
    }
}
*///?}
