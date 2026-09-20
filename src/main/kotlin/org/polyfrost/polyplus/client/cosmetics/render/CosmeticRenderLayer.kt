package org.polyfrost.polyplus.client.cosmetics.render

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.world.entity.EquipmentSlot
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?}

//? if >= 1.21.4 {
import net.minecraft.client.Minecraft
//?}

//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState
*///?}

//? if < 1.21.10 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}

//? if = 1.21.1 {
/*import org.polyfrost.polyplus.client.render.PlayerRenderContext
*///?}

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
    PlayerPreviewRenderer.previewEquipment(entityId)?.let { return it }
    val level = Minecraft.getInstance().level ?: return null
    val entity = level.getEntity(entityId) as? AbstractClientPlayer ?: return null
    if (entity !is PlayerCosmeticsAccess) return null
    return entity.`polyplus$cosmeticEquipment`()
}

private fun resolveParticleColor(entityId: Int): Int? {
    PlayerPreviewRenderer.previewParticleColor(entityId)?.let { return it }
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
    PlayerPreviewRenderer.previewEquipment(player.id)?.let { return it }
    if (player !is PlayerCosmeticsAccess) return null
    return player.`polyplus$cosmeticEquipment`()
}

private fun resolveParticleColor(player: AbstractClientPlayer): Int? =
    PlayerPreviewRenderer.previewParticleColor(player.id)
        ?: CosmeticCatalog.getParticleColor(player.uuid)

private fun resolveChestplateEquipped(player: AbstractClientPlayer): Boolean =
    !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty

private fun resolveHiddenSlots(player: AbstractClientPlayer): Set<BodySlot> = hiddenSlotsFor(player)
*///?}

private fun hiddenSlotsFor(player: AbstractClientPlayer): Set<BodySlot> {
    val hidden = mutableSetOf<BodySlot>()
    if (PolyPlusCosmeticsConfig.hideHeadCosmeticsWithHelmet && !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty) {
        hidden += BodySlot.Hat
    }
    if (PolyPlusCosmeticsConfig.hideFeetCosmeticsWithBoots && !player.getItemBySlot(EquipmentSlot.FEET).isEmpty) {
        hidden += BodySlot.Boots
    }
    return hidden
}
//?}

//? if = 1.8.9 {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.entity.PlayerRenderer
import net.minecraft.client.render.entity.layer.EntityRenderLayer
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.PoseStack

class CosmeticRenderLayer(private val renderer: PlayerRenderer) : EntityRenderLayer<ClientPlayerEntity> {
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
        val equipment = (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`()
        if (equipment.equipped().isEmpty()) return
        val renderContext = PlayerRenderContext.from(player, tickDelta, walkAnimationSpeed, bob)
        val light = if (player.isOnFire) 0xF000F0 else player.getLightLevel(tickDelta)
        CosmeticRenderer.render(
            PoseStack(),
            light,
            renderContext,
            renderer.getModel(),
            equipment,
            CosmeticCatalog.getParticleColor(player.uuid),
            player.getArmor(CHEST) != null,
            hiddenSlotsFor(player),
        )
    }

    override fun colorsWhenDamaged(): Boolean = true

    private fun hiddenSlotsFor(player: ClientPlayerEntity): Set<BodySlot> {
        val hidden = mutableSetOf<BodySlot>()
        if (PolyPlusCosmeticsConfig.hideHeadCosmeticsWithHelmet && player.getArmor(HEAD) != null) {
            hidden += BodySlot.Hat
        }
        if (PolyPlusCosmeticsConfig.hideFeetCosmeticsWithBoots && player.getArmor(FEET) != null) {
            hidden += BodySlot.Boots
        }
        return hidden
    }

    private companion object {
        const val FEET = 0
        const val CHEST = 2
        const val HEAD = 3
    }
}
*///?}
