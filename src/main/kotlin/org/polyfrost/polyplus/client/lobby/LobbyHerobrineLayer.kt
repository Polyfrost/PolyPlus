package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
import net.minecraft.client.renderer.entity.RenderLayerParent
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
        if (LobbyHerobrine.shows(state.id)) super.submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot)
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
        if (LobbyHerobrine.shows(state.id)) super.render(poseStack, bufferSource, lightCoords, state, yRot, xRot)
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
        if (LobbyHerobrine.shows(player.id)) {
            super.render(poseStack, bufferSource, lightCoords, player, limbSwing, limbSwingAmount, partialTicks, ageInTicks, yRot, xRot)
        }
    }
*///?}

    override fun renderType(): RenderType = EYES

    private companion object {
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
        renderer.bindTexture(LobbyHerobrine.EYES)
        GlStateManager.enableBlend()
        GlStateManager.disableAlphaTest()
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE)
        GlStateManager.depthMask(!player.isInvisible)
        lightmap(FULL_BRIGHT)
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        renderer.getModel().render(player, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale)
        lightmap(player.getLightLevel(tickDelta))
        GlStateManager.depthMask(true)
        GlStateManager.disableBlend()
        GlStateManager.enableAlphaTest()
    }

    override fun colorsWhenDamaged(): Boolean = false

    private fun lightmap(light: Int) = GLX.multiTexCoord2f(GLX.GL_TEXTURE1, (light % LIGHT_SPLIT).toFloat(), (light / LIGHT_SPLIT).toFloat())

    private companion object {
        const val FULL_BRIGHT = 0xF000F0
        const val LIGHT_SPLIT = 65536
    }
}
*///?}
