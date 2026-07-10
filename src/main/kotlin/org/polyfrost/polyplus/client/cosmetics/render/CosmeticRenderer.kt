//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics.render

import com.mojang.blaze3d.vertex.PoseStack
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?} elif >= 1.21.4 {
/*import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.state.PlayerRenderState
*///?} else {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import org.polyfrost.polyplus.client.bedrock.render.BedrockAttachedModelRenderer
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.playback.CosmeticPlayback
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PlayerRenderContext

object CosmeticRenderer {
    private const val CHESTPLATE_BACK_OFFSET = 1.5f / 16f

    //? if >= 1.21.10 {
    fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
    ) {
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        val draws = draws(equipment, PlayerRenderContext.from(state), particleColor, chestplateEquipped)
        BedrockAttachedModelRenderer.submit(poseStack, submitNodeCollector, lightCoords, overlay, playerModel, draws)
    }
    //?} elif >= 1.21.4 {
    /*fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        state: PlayerRenderState,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
    ) {
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        val draws = draws(equipment, PlayerRenderContext.from(state), particleColor, chestplateEquipped)
        BedrockAttachedModelRenderer.render(poseStack, bufferSource, lightCoords, overlay, playerModel, draws)
    }
    *///?} else {
    /*fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        player: net.minecraft.client.player.AbstractClientPlayer,
        renderContext: PlayerRenderContext,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
    ) {
        val overlay = LivingEntityRenderer.getOverlayCoords(player, 0f)
        val draws = draws(equipment, renderContext, particleColor, chestplateEquipped)
        BedrockAttachedModelRenderer.render(poseStack, bufferSource, lightCoords, overlay, playerModel, draws)
    }
    *///?}

    private fun draws(
        equipment: CosmeticEquipment,
        renderContext: PlayerRenderContext,
        particleColor: Int?,
        chestplateEquipped: Boolean,
    ): List<BedrockAttachedModelRenderer.DrawCall> =
        equipment.equipped().map { entry ->
            val tinted = entry.cosmetic.slot == BodySlot.Aura && particleColor != null
            val color = if (tinted) particleColor!! else -1
            val translucent = tinted && (color ushr 24) != 0xFF
            val backOffset = if (chestplateEquipped && entry.cosmetic.slot == BodySlot.Backpack) CHESTPLATE_BACK_OFFSET else 0f
            BedrockAttachedModelRenderer.DrawCall(
                model = entry.cosmetic.model,
                texture = entry.cosmetic.texture,
                sample = CosmeticPlayback.sample(entry.cosmetic, entry.startTimeMs, renderContext),
                poseWeight = 1f,
                color = color,
                translucent = translucent,
                backOffset = backOffset,
            )
        }
}
//?}
