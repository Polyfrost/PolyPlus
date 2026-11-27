package org.polyfrost.polyplus.client.cosmetics.render

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.world.entity.EquipmentSlot
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CapeResolver
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.sets.BonusEffects
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack
import java.util.EnumSet
import java.util.UUID

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
        val player = playerFor(state.id)
        PreviewTryOn.withLobby(PreviewTryOn.lobbyTryOn(player)) {
            val previewEquipment = PlayerPreviewRenderer.previewEquipment(state.id)
            val preview = previewEquipment != null
            val equipment = previewEquipment ?: cosmeticEquipmentOf(player) ?: return
            val wearer = if (preview) CosmeticWearers.previewWearerId(state.id) else if (player != null) CosmeticWearers.wearerId(player.uuid) else return
            if (!preview) CosmeticEquips.seen(wearer)
            if (equipment.equipped().isEmpty() && !BonusEffects.active(player?.uuid, preview)) return
            CosmeticRenderer.submit(poseStack, submitNodeCollector, lightCoords, state, parentModel, equipment, resolveParticleColor(state.id, preview, player), resolveChestplateEquipped(player), hiddenSlotsFor(player, equipment), wearer, preview)
        }
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
        val player = playerFor(state.id)
        PreviewTryOn.withLobby(PreviewTryOn.lobbyTryOn(player)) {
            val previewEquipment = PlayerPreviewRenderer.previewEquipment(state.id)
            val preview = previewEquipment != null
            val equipment = previewEquipment ?: cosmeticEquipmentOf(player) ?: return
            val wearer = if (preview) CosmeticWearers.previewWearerId(state.id) else if (player != null) CosmeticWearers.wearerId(player.uuid) else return
            if (!preview) CosmeticEquips.seen(wearer)
            if (equipment.equipped().isEmpty() && !BonusEffects.active(player?.uuid, preview)) return
            CosmeticRenderer.render(poseStack, bufferSource, lightCoords, state, parentModel, equipment, resolveParticleColor(state.id, preview, player), resolveChestplateEquipped(player), hiddenSlotsFor(player, equipment), wearer, preview)
        }
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
        PreviewTryOn.withLobby(PreviewTryOn.lobbyTryOn(player)) {
            val previewEquipment = PlayerPreviewRenderer.previewEquipment(player.id)
            val preview = previewEquipment != null
            val equipment = previewEquipment ?: cosmeticEquipmentOf(player) ?: return
            val wearer = if (preview) CosmeticWearers.previewWearerId(player.id) else CosmeticWearers.wearerId(player.uuid)
            if (!preview) CosmeticEquips.seen(wearer)
            if (equipment.equipped().isEmpty() && !BonusEffects.active(player.uuid, preview)) return
            val renderContext = PlayerRenderContext.from(player, partialTicks, limbSwingAmount, ageInTicks)
            CosmeticRenderer.render(poseStack, bufferSource, lightCoords, player, renderContext, parentModel, equipment, resolveParticleColor(player.id, preview, player), resolveChestplateEquipped(player), hiddenSlotsFor(player, equipment), wearer, preview)
        }
    }
}
*///?}

//? if >= 1.21.4 {
private fun playerFor(entityId: Int): AbstractClientPlayer? =
    Minecraft.getInstance().level?.getEntity(entityId) as? AbstractClientPlayer

private fun resolveParticleColor(entityId: Int, preview: Boolean, player: AbstractClientPlayer?): Int? =
    (if (preview) PlayerPreviewRenderer.previewParticleColor(entityId) else null)
        ?: player?.let { CosmeticCatalog.getParticleColor(it.uuid) }

private fun resolveChestplateEquipped(player: AbstractClientPlayer?): Boolean =
    player?.getItemBySlot(EquipmentSlot.CHEST)?.isEmpty == false
//?} else {
/*private fun resolveParticleColor(entityId: Int, preview: Boolean, player: AbstractClientPlayer): Int? =
    (if (preview) PlayerPreviewRenderer.previewParticleColor(entityId) else null)
        ?: CosmeticCatalog.getParticleColor(player.uuid)

private fun resolveChestplateEquipped(player: AbstractClientPlayer): Boolean =
    !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty
*///?}

private fun cosmeticEquipmentOf(player: AbstractClientPlayer?): CosmeticEquipment? =
    (player as? PlayerCosmeticsAccess)?.`polyplus$cosmeticEquipment`()

private fun isPreview(entityId: Int): Boolean = PlayerPreviewRenderer.previewEquipment(entityId) != null

private val BODY_SLOTS = BodySlot.values()
private val scratchHidden: EnumSet<BodySlot> = EnumSet.noneOf(BodySlot::class.java)

//? if < 1.21.10 {
/*internal fun collectsCosmetics(player: AbstractClientPlayer): Boolean {
    if (isPreview(player.id) || player !is PlayerCosmeticsAccess) return false
    return player.`polyplus$cosmeticEquipment`().equipped().isNotEmpty() || BonusEffects.active(player.uuid, false)
}
*///?}

//? if >= 1.21.4 && < 1.21.10 {
/*internal fun collectCosmetics(poseStack: PoseStack, state: PlayerRenderState, model: PlayerModel) {
    if (isPreview(state.id)) return
    val player = playerFor(state.id)
    PreviewTryOn.withLobby(PreviewTryOn.lobbyTryOn(player)) {
        val equipment = cosmeticEquipmentOf(player) ?: return
        val wearer = CosmeticWearers.wearerId(player!!.uuid)
        CosmeticEquips.seen(wearer)
        if (equipment.equipped().isEmpty() && !BonusEffects.active(player.uuid, false)) return
        CosmeticRenderer.collect(poseStack, state, model, equipment, resolveParticleColor(state.id, false, player), resolveChestplateEquipped(player), hiddenSlotsFor(player, equipment), wearer)
    }
}
*///?} elif = 1.21.1 {
/*internal fun collectCosmetics(
    poseStack: PoseStack,
    player: AbstractClientPlayer,
    model: PlayerModel,
    partialTicks: Float,
    limbSwingAmount: Float,
    ageInTicks: Float,
) {
    if (isPreview(player.id)) return
    PreviewTryOn.withLobby(PreviewTryOn.lobbyTryOn(player)) {
        val equipment = cosmeticEquipmentOf(player) ?: return
        val wearer = CosmeticWearers.wearerId(player.uuid)
        CosmeticEquips.seen(wearer)
        if (equipment.equipped().isEmpty() && !BonusEffects.active(player.uuid, false)) return
        val renderContext = PlayerRenderContext.from(player, partialTicks, limbSwingAmount, ageInTicks)
        CosmeticRenderer.collect(poseStack, player, renderContext, model, equipment, resolveParticleColor(player.id, false, player), resolveChestplateEquipped(player), hiddenSlotsFor(player, equipment), wearer)
    }
}
*///?}

internal fun hiddenSlotsFor(player: AbstractClientPlayer): Set<BodySlot> {
    val hidden = BodySlot.entries.filterNotTo(mutableSetOf()) { PolyPlusCosmeticsConfig.isVisible(it, player.uuid) }
    if (PolyPlusCosmeticsConfig.hideHeadCosmeticsWithHelmet && !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty) {
        hidden += BodySlot.Hat
    }
    if (PolyPlusCosmeticsConfig.hideFeetCosmeticsWithBoots && !player.getItemBySlot(EquipmentSlot.FEET).isEmpty) {
        hidden += BodySlot.Boots
    }
    if (CapeResolver.keepsModCape(player.skin)) hidden += BodySlot.Cape
    return hidden
}

private fun hiddenSlotsFor(player: AbstractClientPlayer?, equipment: CosmeticEquipment): Set<BodySlot> {
    val hidden = scratchHidden
    hidden.clear()
    if (player == null) return hidden
    val uuid = player.uuid
    for (slot in BODY_SLOTS) {
        if (slot != BonusEffects.SLOT && equipment.get(slot) == null) continue
        if (slotHidden(player, uuid, slot)) hidden.add(slot)
    }
    return hidden
}

private fun slotHidden(player: AbstractClientPlayer, uuid: UUID, slot: BodySlot): Boolean = when {
    !PolyPlusCosmeticsConfig.isVisible(slot, uuid) -> true
    slot == BodySlot.Hat -> PolyPlusCosmeticsConfig.hideHeadCosmeticsWithHelmet && !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty
    slot == BodySlot.Boots -> PolyPlusCosmeticsConfig.hideFeetCosmeticsWithBoots && !player.getItemBySlot(EquipmentSlot.FEET).isEmpty
    slot == BodySlot.Cape -> CapeResolver.keepsModCape(player.skin)
    else -> false
}
//?}

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.entity.PlayerRenderer
import net.minecraft.client.render.entity.layer.EntityRenderLayer
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CapeResolver
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.sets.BonusEffects
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack
import java.util.EnumSet
import java.util.UUID

internal fun collectsCosmetics(player: ClientPlayerEntity): Boolean {
    val equipment = (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`()
    return equipment.equipped().isNotEmpty() || BonusEffects.active(player.uuid, false)
}

internal fun collectCosmetics(player: ClientPlayerEntity, model: PlayerModel, walkAnimationSpeed: Float, tickDelta: Float, bob: Float) {
    PreviewTryOn.withLobby(PreviewTryOn.lobbyTryOn(player)) {
        val equipment = (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`()
        val wearer = CosmeticWearers.wearerId(player.uuid)
        CosmeticEquips.seen(wearer)
        if (equipment.equipped().isEmpty() && !BonusEffects.active(player.uuid, false)) return
        val renderContext = PlayerRenderContext.from(player, tickDelta, walkAnimationSpeed, bob)
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        val dx = player.x - dispatcher.cameraX
        val dy = player.y - dispatcher.cameraY
        val dz = player.z - dispatcher.cameraZ
        CosmeticRenderer.collect(
            renderContext,
            model,
            equipment,
            CosmeticCatalog.getParticleColor(player.uuid),
            player.getArmor(CHEST) != null,
            hiddenSlotsFor(player, equipment),
            wearer,
            player.networkId,
            dx * dx + dy * dy + dz * dz,
        )
    }
}

internal fun hiddenSlotsFor(player: ClientPlayerEntity): Set<BodySlot> {
    val hidden = BodySlot.entries.filterNotTo(mutableSetOf()) { PolyPlusCosmeticsConfig.isVisible(it, player.uuid) }
    if (PolyPlusCosmeticsConfig.hideHeadCosmeticsWithHelmet && player.getArmor(HEAD) != null) {
        hidden += BodySlot.Hat
    }
    if (PolyPlusCosmeticsConfig.hideFeetCosmeticsWithBoots && player.getArmor(FEET) != null) {
        hidden += BodySlot.Boots
    }
    if (CapeResolver.keepsModCape(player.capeTextureLocation?.namespace)) hidden += BodySlot.Cape
    return hidden
}

private val BODY_SLOTS = BodySlot.values()
private val scratchHidden: EnumSet<BodySlot> = EnumSet.noneOf(BodySlot::class.java)

private fun hiddenSlotsFor(player: ClientPlayerEntity, equipment: CosmeticEquipment): Set<BodySlot> {
    val hidden = scratchHidden
    hidden.clear()
    val uuid = player.uuid
    for (slot in BODY_SLOTS) {
        if (slot != BonusEffects.SLOT && equipment.get(slot) == null) continue
        if (slotHidden(player, uuid, slot)) hidden.add(slot)
    }
    return hidden
}

private fun slotHidden(player: ClientPlayerEntity, uuid: UUID, slot: BodySlot): Boolean = when {
    !PolyPlusCosmeticsConfig.isVisible(slot, uuid) -> true
    slot == BodySlot.Hat -> PolyPlusCosmeticsConfig.hideHeadCosmeticsWithHelmet && player.getArmor(HEAD) != null
    slot == BodySlot.Boots -> PolyPlusCosmeticsConfig.hideFeetCosmeticsWithBoots && player.getArmor(FEET) != null
    slot == BodySlot.Cape -> CapeResolver.keepsModCape(player.capeTextureLocation?.namespace)
    else -> false
}

private const val FEET = 0
private const val CHEST = 2
private const val HEAD = 3

class CosmeticRenderLayer(private val renderer: PlayerRenderer) : EntityRenderLayer<ClientPlayerEntity> {
    private val sharedPoseStack = PoseStack()

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
        PreviewTryOn.withLobby(PreviewTryOn.lobbyTryOn(player)) {
            val equipment = (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`()
            val wearer = CosmeticWearers.wearerId(player.uuid)
            CosmeticEquips.seen(wearer)
            if (equipment.equipped().isEmpty() && !BonusEffects.active(player.uuid, false)) return
            val renderContext = PlayerRenderContext.from(player, tickDelta, walkAnimationSpeed, bob)
            val light = if (player.isOnFire) 0xF000F0 else player.getLightLevel(tickDelta)
            val dispatcher = Minecraft.getInstance().entityRenderDispatcher
            val dx = player.x - dispatcher.cameraX
            val dy = player.y - dispatcher.cameraY
            val dz = player.z - dispatcher.cameraZ
            sharedPoseStack.last().pose().identity()
            sharedPoseStack.last().normal().identity()
            CosmeticRenderer.render(
                sharedPoseStack,
                light,
                renderContext,
                renderer.getModel(),
                equipment,
                CosmeticCatalog.getParticleColor(player.uuid),
                player.getArmor(CHEST) != null,
                hiddenSlotsFor(player, equipment),
                wearer,
                player.networkId,
                dx * dx + dy * dy + dz * dz,
                false,
            )
        }
    }

    override fun colorsWhenDamaged(): Boolean = true
}
*///?}
