package org.polyfrost.polyplus.client.emotes.effects

import net.minecraft.client.renderer.entity.LivingEntityRenderer
import org.polyfrost.polyplus.client.bedrock.render.BedrockAttachedModelRenderer
import org.polyfrost.polyplus.client.emotes.playback.EmotePlaybackSnapshot
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?}

//? if >= 1.21.4 {
import org.polyfrost.polyplus.client.render.PlayerRenderContext
//?}

//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState
*///?}

//? if < 1.21.10 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}

//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer
*///?}

object EmoteEffectRenderer {
    //? if >= 1.21.10 {
    fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        playerModel: PlayerModel,
        snapshot: EmotePlaybackSnapshot,
    ) {
        val renderContext = PlayerRenderContext.from(state)
        if (renderContext.isInvisible || snapshot.playerWeight <= 0f) return
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        BedrockAttachedModelRenderer.submit(poseStack, submitNodeCollector, lightCoords, overlay, playerModel, draws(snapshot))
    }
    //?} elif >= 1.21.4 {
    /*fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        state: PlayerRenderState,
        playerModel: PlayerModel,
        snapshot: EmotePlaybackSnapshot,
    ) {
        val renderContext = PlayerRenderContext.from(state)
        if (renderContext.isInvisible || snapshot.playerWeight <= 0f) return
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        BedrockAttachedModelRenderer.render(poseStack, bufferSource, lightCoords, overlay, playerModel, draws(snapshot))
    }
    *///?} else {
    /*fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        player: AbstractClientPlayer,
        playerModel: PlayerModel,
        snapshot: EmotePlaybackSnapshot,
    ) {
        if (player.isInvisible || snapshot.playerWeight <= 0f) return
        val overlay = LivingEntityRenderer.getOverlayCoords(player, 0f)
        BedrockAttachedModelRenderer.render(poseStack, bufferSource, lightCoords, overlay, playerModel, draws(snapshot))
    }
    *///?}

    private fun draws(snapshot: EmotePlaybackSnapshot): List<BedrockAttachedModelRenderer.DrawCall> =
        snapshot.emote.effects.map { effect ->
            BedrockAttachedModelRenderer.DrawCall(
                model = effect.model,
                texture = effect.texture,
                sample = snapshot.sample,
                poseWeight = snapshot.effectPoseWeight,
            )
        }
}
