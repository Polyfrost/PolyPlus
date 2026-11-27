package org.polyfrost.polyplus.client.cosmetics.render

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.pets.PetRenderHook
import org.polyfrost.polyplus.client.render.PoseStack

//? if > 1.8.9 {
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.player.Player
import org.polyfrost.polyplus.client.bedrock.render.BedrockAttachedModelRenderer
//?} else {
/*import net.minecraft.client.render.model.ModelPart
import net.minecraft.entity.living.player.PlayerEntity
import org.polyfrost.polyplus.client.render.VertexConsumer
*///?}

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}

object FirstPersonHands {
    private const val RIGHT_HAND = 0x6a09e667f3bcc908L
    private const val LEFT_HAND = -0x4498517a7b3558c5L

    private val root = FloatArray(12)
    private var eventsFrame = -1
    private var events = 0

    private class Hand(val scene: CosmeticScene, val frame: Int, val row: Int, val texture: Identifier)

    internal fun owns(owner: Long, wearer: Long): Boolean =
        wearer == wearerId(owner, true) || wearer == wearerId(owner, false)

    internal fun breakRings(owner: Long) {
        CosmeticWearers.breakRings(wearerId(owner, true))
        CosmeticWearers.breakRings(wearerId(owner, false))
    }

    private fun wearerId(owner: Long, right: Boolean): Long = owner xor if (right) RIGHT_HAND else LEFT_HAND

    private fun prepare(arm: ModelPart, right: Boolean): Hand? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        if (!FirstPersonView.ownEyes()) return null
        val slot = if (right) BodySlot.RightHand else BodySlot.LeftHand
        if (!PolyPlusCosmeticsConfig.isVisible(slot, player.uuid)) return null
        val equipment = (player as? PlayerCosmeticsAccess)?.`polyplus$cosmeticEquipment`() ?: return null
        val cosmetic = equipment.get(slot)?.cosmetic ?: return null
        if (cosmetic.scene.isEmpty || !RemoteTextures.isRegistered(cosmetic.texture)) return null
        CosmeticWearers.begin()
        val owner = CosmeticWearers.wearerId(player.uuid)
        val wearer = CosmeticWearers.track(wearerId(owner, right), entityId(player), 0.0, false)
        wearer.firstPerson = true
        wearer.hand = true
        val partialTick = FirstPersonEffects.partialTick(mc)
        FirstPersonEffects.place(player, partialTick, root)
        val signals = PlayerSignals.sample(player, partialTick)
        if (eventsFrame != CosmeticWearers.frame) {
            eventsFrame = CosmeticWearers.frame
            events = signals.events(PlayerSignals.READER_HANDS)
        }
        val row = cosmetic.scene.addArm(wearer, arm, right, signals, events, age(player) + partialTick, root)
        if (row < 0) return null
        return Hand(cosmetic.scene, CosmeticWearers.frame, row, CosmeticRenderer.texture(wearer, cosmetic, player.uuid))
    }

    //? if > 1.8.9 {
    private fun entityId(player: Player): Int = player.id

    private fun age(player: Player): Float = player.tickCount.toFloat()
    //?} else {
    /*private fun entityId(player: PlayerEntity): Int = player.networkId

    private fun age(player: PlayerEntity): Float = player.ticks.toFloat()
    *///?}

    //? if >= 1.21.10 {
    @JvmStatic
    fun submit(poseStack: PoseStack, collector: SubmitNodeCollector, light: Int, arm: ModelPart, right: Boolean) {
        PetRenderHook.submitFirstPerson(poseStack, collector, light, arm, right)
        val hand = prepare(arm, right) ?: return
        collector.submitCustomGeometry(poseStack, BedrockAttachedModelRenderer.renderType(hand.texture, RemoteTextures.isTranslucent(hand.texture))) { pose, buffer ->
            hand.scene.emit(hand.frame, hand.row, pose, buffer, light, OverlayTexture.NO_OVERLAY, -1)
        }
    }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun render(poseStack: PoseStack, buffers: MultiBufferSource, light: Int, arm: ModelPart, right: Boolean) {
        PetRenderHook.renderFirstPerson(poseStack, buffers, light, arm, right)
        val hand = prepare(arm, right) ?: return
        val buffer = buffers.getBuffer(BedrockAttachedModelRenderer.renderType(hand.texture, RemoteTextures.isTranslucent(hand.texture)))
        hand.scene.emit(hand.frame, hand.row, poseStack.last(), buffer, light, OverlayTexture.NO_OVERLAY, -1)
    }
    *///?} else {
    /*private val identity = PoseStack()

    @JvmStatic
    fun render(player: PlayerEntity, arm: ModelPart, right: Boolean) {
        PetRenderHook.renderFirstPerson(player, arm, right)
        val hand = prepare(arm, right) ?: return
        val partialTick = FirstPersonEffects.partialTick(Minecraft.getInstance())
        val light = if (player.isOnFire) FULL_BRIGHT else player.getLightLevel(partialTick)
        VertexConsumer.draw(hand.texture, RemoteTextures.isTranslucent(hand.texture)) { buffer ->
            hand.scene.emit(hand.frame, hand.row, identity.last(), buffer, light, 0, -1)
        }
    }

    private const val FULL_BRIGHT = 0xF000F0
    *///?}
}
