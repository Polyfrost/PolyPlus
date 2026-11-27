package org.polyfrost.polyplus.client.cosmetics.render

import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.pets.PetAsset
import org.polyfrost.polyplus.client.pets.PetAssets
import org.polyfrost.polyplus.client.pets.PetManager

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.world.entity.player.Player
//?} else {
/*import net.minecraft.client.Minecraft
import net.minecraft.entity.living.player.PlayerEntity as Player
import net.minecraft.network.packet.s2c.play.EntityTeleportS2CPacket
*///?}

//? if >= 1.21.4 {
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.world.entity.Relative
//?}

//? if >= 26.3 {
import net.minecraft.world.entity.PositionPath
//?}

object WearerJumps {
    private val gate = WearerJumpGate()
    private val slides = WearerSlides()
    private var tick = 0L
    private var syncing = false
    private var syncX = 0.0
    private var syncY = 0.0
    private var syncZ = 0.0

    init {
        eventHandler<TickEvent.End> { endTick() }
    }

    private fun endTick() {
        tick++
        if (slides.count == 0) return
        for (i in 0 until slides.count) {
            val player = player(slides.id(i)) ?: continue
            broke(player)
        }
        slides.advance()
    }

    @JvmStatic
    fun entityTeleported(entityId: Int, x: Double, y: Double, z: Double, relativeX: Boolean, relativeY: Boolean, relativeZ: Boolean) {
        val player = player(entityId) ?: return
        val dx = WearerJumpRules.offset(relativeX, x, player.x)
        val dy = WearerJumpRules.offset(relativeY, y, player.y)
        val dz = WearerJumpRules.offset(relativeZ, z, player.z)
        if (!WearerJumpRules.jumped(dx, dy, dz)) return
        broke(player)
        if (player !== Minecraft.getInstance().player) slides.start(entityId)
    }

    @JvmStatic
    fun entityTeleported(entityId: Int, x: Double, y: Double, z: Double) {
        entityTeleported(entityId, x, y, z, false, false, false)
    }

    //? if >= 1.21.4 {
    @JvmStatic
    fun entityTeleported(packet: ClientboundTeleportEntityPacket) {
        val to = packet.change().position()
        val relatives = packet.relatives()
        entityTeleported(
            packet.id(), to.x, to.y, to.z,
            Relative.X in relatives, Relative.Y in relatives, Relative.Z in relatives,
        )
    }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun entityTeleported(packet: ClientboundTeleportEntityPacket) {
        entityTeleported(packet.id, packet.x, packet.y, packet.z)
    }
    *///?} else {
    /*@JvmStatic
    fun entityTeleported(packet: EntityTeleportS2CPacket) {
        entityTeleported(packet.id, packet.x / FIXED_POINT, packet.y / FIXED_POINT, packet.z / FIXED_POINT)
    }

    private const val FIXED_POINT = 32.0
    *///?}

    //? if >= 26.3 {
    @JvmStatic
    fun entitySynced(packet: ClientboundEntityPositionSyncPacket) {
        val path = packet.position()
        if (path.type() != PositionPath.Type.LINEAR) return
        val to = path.endPosition()
        entityTeleported(packet.id(), to.x, to.y, to.z)
    }
    //?} elif >= 1.21.4 {
    /*@JvmStatic
    fun entitySynced(packet: ClientboundEntityPositionSyncPacket) {
        val to = packet.values().position()
        entityTeleported(packet.id(), to.x, to.y, to.z)
    }
    *///?}

    @JvmStatic
    fun entityAppeared(entityId: Int) {
        broke(player(entityId) ?: return)
    }

    @JvmStatic
    fun localSyncStarted() {
        val player = Minecraft.getInstance().player
        syncing = player != null
        if (player == null) return
        syncX = player.x
        syncY = player.y
        syncZ = player.z
    }

    @JvmStatic
    fun localSyncEnded() {
        if (!syncing) return
        syncing = false
        val player = Minecraft.getInstance().player ?: return
        if (WearerJumpRules.jumped(player.x - syncX, player.y - syncY, player.z - syncZ)) broke(player)
    }

    @JvmStatic
    fun localRespawned() {
        broke(Minecraft.getInstance().player ?: return)
    }

    fun broke(player: Player) {
        val owner = CosmeticWearers.wearerId(player.uuid)
        if (!gate.first(owner, tick)) return
        CosmeticWearers.breakRings(owner)
        FirstPersonHands.breakRings(owner)
        (player as? PlayerEmotesAccess)?.`polyplus$emoteController`()?.breakRings()
        val pet = PetManager.pet(player.uuid) ?: return
        if (WearerJumpRules.petJumpsWithOwner(assetOf(pet)?.archetype)) broke(pet)
    }

    internal fun broke(pet: PetManager.Pet) {
        if (!gate.first(pet.wearer, tick)) return
        assetOf(pet)?.breakRings(pet.wearer)
    }

    private fun assetOf(pet: PetManager.Pet): PetAsset? = pet.asset ?: PetAssets.get(pet.cosmeticId)

    //? if > 1.8.9 {
    private fun player(entityId: Int): Player? = Minecraft.getInstance().level?.getEntity(entityId) as? Player
    //?} else {
    /*private fun player(entityId: Int): Player? = Minecraft.getInstance().world?.getEntity(entityId) as? Player
    *///?}
}
