//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics.render

import com.mojang.blaze3d.vertex.PoseStack
//? if >= 1.21.4
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
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
import net.minecraft.world.entity.EquipmentSlot
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PlayerRenderContext

//? if >= 1.21.10 {
class CosmeticRenderLayer(renderer: RenderLayerParent<AvatarRenderState, PlayerModel>) :
    RenderLayer<AvatarRenderState, PlayerModel>(renderer) {

    override fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        val equipment = resolveEquipment(state.id) ?: return
        if (equipment.equipped().isEmpty()) return
        CosmeticRenderer.submit(poseStack, submitNodeCollector, lightCoords, state, parentModel, equipment, resolveParticleColor(state.id), resolveChestplateEquipped(state.id), resolveHiddenSlots(state.id))
    }
}
//?} elif >= 1.21.4 {
/*class CosmeticRenderLayer(renderer: RenderLayerParent<PlayerRenderState, PlayerModel>) :
    RenderLayer<PlayerRenderState, PlayerModel>(renderer) {

    override fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        state: PlayerRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        val equipment = resolveEquipment(state.id) ?: return
        if (equipment.equipped().isEmpty()) return
        CosmeticRenderer.render(poseStack, bufferSource, lightCoords, state, parentModel, equipment, resolveParticleColor(state.id), resolveChestplateEquipped(state.id), resolveHiddenSlots(state.id))
    }
}
*///?} else {
/*class CosmeticRenderLayer(renderer: RenderLayerParent<AbstractClientPlayer, PlayerModel>) :
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
        val equipment = resolveEquipment(player) ?: return
        if (equipment.equipped().isEmpty()) return
        val renderContext = PlayerRenderContext.from(player, partialTicks, limbSwingAmount, ageInTicks)
        CosmeticRenderer.render(poseStack, bufferSource, lightCoords, player, renderContext, parentModel, equipment, resolveParticleColor(player), resolveChestplateEquipped(player), resolveHiddenSlots(player))
    }
}
*///?}

//? if >= 1.21.4 {
private fun resolveEquipment(entityId: Int): CosmeticEquipment? {
    org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer.previewEquipment(entityId)?.let { return it }
    val level = Minecraft.getInstance().level ?: return null
    val entity = level.getEntity(entityId) as? AbstractClientPlayer ?: return null
    if (entity !is PlayerCosmeticsAccess) return null
    return entity.`polyplus$cosmeticEquipment`()
}

private fun resolveParticleColor(entityId: Int): Int? {
    org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer.previewParticleColor(entityId)?.let { return it }
    val level = Minecraft.getInstance().level ?: return null
    val entity = level.getEntity(entityId) as? AbstractClientPlayer ?: return null
    return CosmeticCatalog.getParticleColor(entity.uuid)
}

private fun resolveChestplateEquipped(entityId: Int): Boolean {
    val level = Minecraft.getInstance().level ?: return false
    val entity = level.getEntity(entityId) as? AbstractClientPlayer ?: return false
    return !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty
}

private fun resolveHiddenSlots(entityId: Int): Set<BodySlot> {
    val level = Minecraft.getInstance().level ?: return emptySet()
    val entity = level.getEntity(entityId) as? AbstractClientPlayer ?: return emptySet()
    return hiddenSlotsFor(entity)
}
//?} else {
/*private fun resolveEquipment(player: AbstractClientPlayer): CosmeticEquipment? {
    org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer.previewEquipment(player.id)?.let { return it }
    if (player !is PlayerCosmeticsAccess) return null
    return player.`polyplus$cosmeticEquipment`()
}

private fun resolveParticleColor(player: AbstractClientPlayer): Int? =
    org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer.previewParticleColor(player.id)
        ?: CosmeticCatalog.getParticleColor(player.uuid)

private fun resolveChestplateEquipped(player: AbstractClientPlayer): Boolean =
    !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty

private fun resolveHiddenSlots(player: AbstractClientPlayer): Set<BodySlot> = hiddenSlotsFor(player)
*///?}

private fun hiddenSlotsFor(player: AbstractClientPlayer): Set<BodySlot> {
    val hidden = mutableSetOf<BodySlot>()
    if (PolyPlusConfig.hideHeadCosmeticsWithHelmet && !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty) {
        hidden += BodySlot.Hat
    }
    if (PolyPlusConfig.hideFeetCosmeticsWithBoots && !player.getItemBySlot(EquipmentSlot.FEET).isEmpty) {
        hidden += BodySlot.Boots
    }
    return hidden
}
//?}
