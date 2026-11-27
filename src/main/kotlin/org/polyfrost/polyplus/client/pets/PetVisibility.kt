package org.polyfrost.polyplus.client.pets

import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.UUID

//? if > 1.8.9 {
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
//?} else {
/*import net.minecraft.client.render.Culler
import net.minecraft.entity.living.LivingEntity
import net.minecraft.entity.living.player.PlayerEntity
import net.minecraft.util.math.Box
*///?}

internal object PetVisibility {
    private const val SIGHT_SECONDS = 0.2
    private const val SIGHT_KEEP_SECONDS = 5.0
    private const val CAMERA_CLEARANCE = 0.2

    private class Sight {
        var checkedAt = Double.NEGATIVE_INFINITY
        var visible = true
    }

    private val sights = HashMap<UUID, Sight>()
    private var prunedAt = Double.NEGATIVE_INFINITY

    fun sightDue(checkedAt: Double, now: Double): Boolean = now - checkedAt >= SIGHT_SECONDS || now < checkedAt

    fun pruneSights(now: Double) {
        if (now - prunedAt < SIGHT_KEEP_SECONDS) return
        prunedAt = now
        sights.values.removeIf { now - it.checkedAt > SIGHT_KEEP_SECONDS }
    }

    fun clearOfCamera(dx: Double, dy: Double, dz: Double, halfWidth: Double, height: Double): Boolean {
        val reach = halfWidth + CAMERA_CLEARANCE
        return dx < -reach || dx > reach || dz < -reach || dz > reach ||
            dy > CAMERA_CLEARANCE || dy + height < -CAMERA_CLEARANCE
    }

    //? if > 1.8.9 {
    var frustum: Frustum? = null
    //?} else {
    /*var frustum: Culler? = null
    *///?}

    //? if > 1.8.9 {
    fun ownerShown(owner: Player): Boolean =
        PolyPlusCosmeticsConfig.isVisible(BodySlot.Pet, owner.uuid) && !owner.isInvisible && !owner.isSpectator

    fun shown(owner: Player, local: UUID, now: Double): Boolean {
        if (!ownerShown(owner)) return false
        val mc = Minecraft.getInstance()
        val camera = mc.cameraEntity
        if (owner.uuid == local || camera === owner) return true
        val sight = sights.getOrPut(owner.uuid) { Sight() }
        if (sightDue(sight.checkedAt, now)) {
            sight.checkedAt = now
            val viewer = camera as? LivingEntity ?: mc.player
            sight.visible = viewer == null || viewer === owner || viewer.hasLineOfSight(owner)
        }
        return sight.visible
    }

    fun inView(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Boolean =
        frustum?.isVisible(AABB(minX, minY, minZ, maxX, maxY, maxZ)) ?: true
    //?} else {
    /*fun ownerShown(owner: PlayerEntity): Boolean =
        PolyPlusCosmeticsConfig.isVisible(BodySlot.Pet, owner.uuid) && !owner.isInvisible && !owner.isSpectator

    fun shown(owner: PlayerEntity, local: UUID, now: Double): Boolean {
        if (!ownerShown(owner)) return false
        val mc = Minecraft.getInstance()
        val camera = mc.camera
        if (owner.uuid == local || camera === owner) return true
        val sight = sights.getOrPut(owner.uuid) { Sight() }
        if (sightDue(sight.checkedAt, now)) {
            sight.checkedAt = now
            val viewer = camera as? LivingEntity ?: mc.player
            sight.visible = viewer == null || viewer === owner || viewer.canSee(owner)
        }
        return sight.visible
    }

    fun inView(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Boolean =
        frustum?.isVisible(Box(minX, minY, minZ, maxX, maxY, maxZ)) ?: true
    *///?}
}
