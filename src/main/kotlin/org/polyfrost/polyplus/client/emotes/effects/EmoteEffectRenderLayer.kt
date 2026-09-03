package org.polyfrost.polyplus.client.emotes.effects

import com.mojang.blaze3d.vertex.PoseStack
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
//? if < 1.21.4
//import net.minecraft.client.player.AbstractClientPlayer
//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?} elif >= 1.21.4 {
/*import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.state.PlayerRenderState
*///?} else {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
//? if >= 1.21.4
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.emotes.playback.EmoteController

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
