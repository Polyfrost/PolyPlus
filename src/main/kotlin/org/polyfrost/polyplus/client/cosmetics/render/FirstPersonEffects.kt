package org.polyfrost.polyplus.client.cosmetics.render

import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.sets.BonusEffects
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

//? if > 1.8.9 {
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
//?} else {
/*import net.minecraft.entity.living.player.PlayerEntity
import org.polyfrost.polyplus.mixin.client.signals.SignalsTimerAccessor
*///?}

internal object FirstPersonEffects {
    private const val PLAYER_SCALE = 0.9375f
    private val root = FloatArray(12)
    private val BODY_SLOTS = BodySlot.values()

    fun add() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val id = CosmeticWearers.wearerId(player.uuid)
        if (CosmeticWearers.trackedThisFrame(id)) return
        CosmeticEquips.seen(id)
        if (player.isInvisible || player.isSpectator) return
        val equipment = (player as? PlayerCosmeticsAccess)?.`polyplus$cosmeticEquipment`() ?: return
        var wearer: CosmeticWearers.Wearer? = null
        var events = 0
        val partialTick = partialTick(mc)
        val signals = PlayerSignals.sample(player, partialTick)
        val bonuses = BonusEffects.forPlayer(player.uuid)
        val worn = BODY_SLOTS.size
        for (index in 0 until worn + bonuses.size) {
            val bonus = if (index >= worn) bonuses[index - worn] else null
            val cosmetic = bonus?.cosmetic ?: equipment.get(BODY_SLOTS[index])?.cosmetic ?: continue
            if (!cosmetic.scene.hasEffects || !PolyPlusCosmeticsConfig.isVisible(cosmetic.slot, player.uuid)) continue
            val tracked = wearer ?: CosmeticWearers.track(id, entityId(player), 0.0, false).also {
                it.firstPerson = true
                events = signals.events(PlayerSignals.READER_COSMETICS)
                place(player, partialTick, root)
                wearer = it
            }
            EffectColors.sync(cosmetic, id, player.uuid)
            val equip = if (bonus == null) CosmeticEquips.events(tracked, cosmetic) else CosmeticEquips.bonusEvents(tracked, player.uuid, bonus)
            cosmetic.scene.addBare(tracked, signals, events or equip, root)
        }
    }

    //? if > 1.8.9 {
    fun partialTick(mc: Minecraft): Float =
        //? if >= 1.21.4 {
        mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        //?} else {
        /*mc.timer.getGameTimeDeltaPartialTick(false)
        *///?}

    private fun entityId(player: Player): Int = player.id

    fun place(player: Player, partialTick: Float, out: FloatArray) {
        val position = player.getPosition(partialTick)
        val yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)
        CosmeticOrigin.fromWorld(out, position.x, position.y, position.z, yaw, PLAYER_SCALE)
    }
    //?} else {
    /*fun partialTick(mc: Minecraft): Float = (mc as SignalsTimerAccessor).`polyplus$signalTimer`().partialTick

    private fun entityId(player: PlayerEntity): Int = player.networkId

    fun place(player: PlayerEntity, partialTick: Float, out: FloatArray) {
        val x = player.prevX + (player.x - player.prevX) * partialTick
        val y = player.prevY + (player.y - player.prevY) * partialTick
        val z = player.prevZ + (player.z - player.prevZ) * partialTick
        var turn = (player.bodyYaw - player.lastBodyYaw) % 360f
        if (turn >= 180f) turn -= 360f
        if (turn < -180f) turn += 360f
        CosmeticOrigin.fromWorld(out, x, y, z, player.lastBodyYaw + turn * partialTick, PLAYER_SCALE)
    }
    *///?}
}
