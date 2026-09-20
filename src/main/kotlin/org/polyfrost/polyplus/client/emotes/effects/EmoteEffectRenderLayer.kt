package org.polyfrost.polyplus.client.emotes.effects

//? if > 1.8.9 {
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
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
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
*///?}

//? if >= 1.21.10 {
class EmoteEffectRenderLayer(renderer: RenderLayerParent<AvatarRenderState, PlayerModel>) : RenderLayer<AvatarRenderState, PlayerModel>(renderer) {
    override fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        val snapshot = resolveController(state)?.playbackSnapshot() ?: return
        if (snapshot.emote.effects.isEmpty()) return
        EmoteEffectRenderer.submit(poseStack, submitNodeCollector, lightCoords, state, parentModel, snapshot)
    }
}
//?} elif >= 1.21.4 {
/*class EmoteEffectRenderLayer(renderer: RenderLayerParent<PlayerRenderState, PlayerModel>) : RenderLayer<PlayerRenderState, PlayerModel>(renderer) {
    override fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        state: PlayerRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        val snapshot = resolveController(state)?.playbackSnapshot() ?: return
        if (snapshot.emote.effects.isEmpty()) return
        EmoteEffectRenderer.render(poseStack, bufferSource, lightCoords, state, parentModel, snapshot)
    }
}
*///?} else {
/*class EmoteEffectRenderLayer(renderer: RenderLayerParent<AbstractClientPlayer, PlayerModel>) :
    RenderLayer<AbstractClientPlayer, PlayerModel>(renderer) {
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
        val snapshot = resolveController(player)?.playbackSnapshot() ?: return
        if (snapshot.emote.effects.isEmpty()) return
        EmoteEffectRenderer.render(poseStack, bufferSource, lightCoords, player, parentModel, snapshot)
    }
}
*///?}

//? if >= 1.21.4 {
private fun resolveController(state: Any): EmoteController? {
    if (state !is AvatarEmoteRenderAccess) return null
    val controller = state.`polyplus$boundEmoteController`()
    return controller.takeIf { it.isActive }
}
//?} else {
/*private fun resolveController(player: AbstractClientPlayer): EmoteController? {
    if (player !is PlayerEmotesAccess) return null
    val controller = player.`polyplus$emoteController`()
    return controller.takeIf { it.isActive }
}
*///?}
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.entity.PlayerRenderer
import net.minecraft.client.render.entity.layer.EntityRenderLayer
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.render.PoseStack

class EmoteEffectRenderLayer(private val renderer: PlayerRenderer) : EntityRenderLayer<ClientPlayerEntity> {
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
        val controller = (player as PlayerEmotesAccess).`polyplus$emoteController`().takeIf { it.isActive } ?: return
        val snapshot = controller.playbackSnapshot() ?: return
        if (snapshot.emote.effects.isEmpty()) return
        val light = if (player.isOnFire) 0xF000F0 else player.getLightLevel(tickDelta)
        EmoteEffectRenderer.render(PoseStack(), light, player, renderer.getModel(), snapshot)
    }

    override fun colorsWhenDamaged(): Boolean = true
}
*///?}
