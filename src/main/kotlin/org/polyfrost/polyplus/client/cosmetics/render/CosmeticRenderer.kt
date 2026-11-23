package org.polyfrost.polyplus.client.cosmetics.render

//? if > 1.8.9 {
import net.minecraft.client.renderer.entity.LivingEntityRenderer
//?}
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.bedrock.playback.BedrockAnimationPlayback
import org.polyfrost.polyplus.client.bedrock.render.BedrockAttachedModelRenderer
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?}

//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState
*///?}

//? if < 1.21.10 && > 1.8.9 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}

//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer
*///?}

//? if = 1.8.9
//import org.polyfrost.polyplus.client.render.VertexConsumer

object CosmeticRenderer {
    private const val CHESTPLATE_BACK_OFFSET = 1.5f

    private class Draw(val texture: Identifier, val mesh: CosmeticMesh, val color: Int, val translucent: Boolean)

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
        hiddenSlots: Set<BodySlot>,
    ) {
        val renderContext = PlayerRenderContext.from(state)
        if (renderContext.isInvisible) return
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        for (draw in draws(equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots)) {
            val renderType = BedrockAttachedModelRenderer.renderType(draw.texture, draw.translucent)
            submitNodeCollector.submitCustomGeometry(poseStack, renderType) { pose, buffer ->
                draw.mesh.emit(pose, buffer, lightCoords, overlay, draw.color)
            }
        }
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
        hiddenSlots: Set<BodySlot>,
    ) {
        val renderContext = PlayerRenderContext.from(state)
        if (renderContext.isInvisible) return
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        for (draw in draws(equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots)) {
            val buffer = bufferSource.getBuffer(BedrockAttachedModelRenderer.renderType(draw.texture, draw.translucent))
            draw.mesh.emit(poseStack.last(), buffer, lightCoords, overlay, draw.color)
        }
    }
    *///?} elif > 1.8.9 {
    /*fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        player: AbstractClientPlayer,
        renderContext: PlayerRenderContext,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
    ) {
        if (renderContext.isInvisible) return
        val overlay = LivingEntityRenderer.getOverlayCoords(player, 0f)
        for (draw in draws(equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots)) {
            val buffer = bufferSource.getBuffer(BedrockAttachedModelRenderer.renderType(draw.texture, draw.translucent))
            draw.mesh.emit(poseStack.last(), buffer, lightCoords, overlay, draw.color)
        }
    }
    *///?} else {
    /*fun render(
        poseStack: PoseStack,
        lightCoords: Int,
        renderContext: PlayerRenderContext,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
    ) {
        if (renderContext.isInvisible) return
        for (draw in draws(equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots)) {
            VertexConsumer.draw(draw.texture, draw.translucent) { buffer ->
                draw.mesh.emit(poseStack.last(), buffer, lightCoords, 0, draw.color)
            }
        }
    }
    *///?}

    private fun draws(
        equipment: CosmeticEquipment,
        renderContext: PlayerRenderContext,
        playerModel: PlayerModel,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
    ): List<Draw> = equipment.equipped().mapNotNull { entry ->
        val cosmetic = entry.cosmetic
        if (cosmetic.slot in hiddenSlots || !RemoteTextures.isRegistered(cosmetic.texture)) return@mapNotNull null
        val tint = particleColor?.takeIf { cosmetic.slot == BodySlot.Aura }
        val backOffset = if (chestplateEquipped && cosmetic.slot == BodySlot.Backpack) CHESTPLATE_BACK_OFFSET else 0f
        val seconds = BedrockAnimationPlayback.elapsedTicksSince(entry.startTimeMs) / BedrockConstants.TICKS_PER_SECOND
        val mesh = cosmetic.scene.build(playerModel, seconds, renderContext, backOffset) ?: return@mapNotNull null
        Draw(cosmetic.texture, mesh, tint ?: -1, translucent = tint != null && (tint ushr 24) != 0xFF)
    }
}
