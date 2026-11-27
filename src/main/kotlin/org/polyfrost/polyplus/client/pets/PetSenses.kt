package org.polyfrost.polyplus.client.pets

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import java.util.UUID

//? if > 1.8.9 {
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
//?} else {
/*import net.minecraft.entity.ItemEntity
import net.minecraft.entity.living.mob.MobEntity as Mob
import net.minecraft.entity.living.mob.monster.Monster as Enemy
import net.minecraft.entity.living.player.PlayerEntity as Player
import net.minecraft.entity.particle.ParticleType
import net.minecraft.util.math.Box as AABB
*///?}

internal class PetSight {
    var kind = PetSenses.NONE
    var entity: Entity? = null
    var pet: PetManager.Pet? = null
    var x = 0.0
    var y = 0.0
    var z = 0.0

    fun clear() {
        kind = PetSenses.NONE
        entity = null
        pet = null
    }

    fun copy(other: PetSight) {
        kind = other.kind
        entity = other.entity
        pet = other.pet
        x = other.x
        y = other.y
        z = other.z
    }
}

internal object PetSenses {
    const val NONE = 0
    const val OWNER = 1
    const val PLAYER = 2
    const val PET = 3
    const val ITEM = 4
    const val MOB = 5
    const val HOSTILE = 6

    private const val ITEM_LIFT = 0.25
    private const val PET_LIFT = 0.3
    private const val POOF_COUNT = 6
    private const val POOF_SPREAD = 0.3
    private const val POOF_RISE = 0.03

    fun scan(
        level: ClientLevel,
        x: Double,
        y: Double,
        z: Double,
        radius: Double,
        owner: UUID,
        out: Array<PetSight>,
    ): Int {
        var count = 0
        val area = AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius)
        val found = entities(level, area)
        for (index in found.indices) {
            if (count >= out.size) break
            val entity = found[index]
            if (gone(entity) || entity.isInvisible) continue
            val kind = when (entity) {
                is Player -> if (entity.uuid == owner || entity.isSpectator) NONE else PLAYER
                is ItemEntity -> ITEM
                is Enemy -> HOSTILE
                is Mob -> MOB
                else -> NONE
            }
            if (kind == NONE) continue
            val sight = out[count++]
            sight.clear()
            sight.kind = kind
            sight.entity = entity
            locate(sight)
        }
        return count
    }

    fun track(sight: PetSight): Boolean {
        val pet = sight.pet
        if (pet != null) {
            if (!pet.present) return false
            sight.x = pet.body.x
            sight.y = pet.body.y + PET_LIFT
            sight.z = pet.body.z
            return true
        }
        val entity = sight.entity ?: return sight.kind == OWNER
        if (gone(entity)) return false
        locate(sight)
        return true
    }

    fun poof(level: ClientLevel, x: Double, y: Double, z: Double, seed: Long) {
        for (i in 0 until POOF_COUNT) {
            val ox = (PetMind.unit(seed, i.toLong()) - 0.5) * 2.0 * POOF_SPREAD
            val oy = PetMind.unit(seed, i + 16L) * POOF_SPREAD * 2.0
            val oz = (PetMind.unit(seed, i + 32L) - 0.5) * 2.0 * POOF_SPREAD
            //? if > 1.8.9 {
            level.addParticle(ParticleTypes.POOF, x + ox, y + oy, z + oz, 0.0, POOF_RISE, 0.0)
            //?} else {
            /*level.addParticle(ParticleType.CLOUD, x + ox, y + oy, z + oz, 0.0, POOF_RISE, 0.0)
            *///?}
        }
    }

    private fun locate(sight: PetSight) {
        val entity = sight.entity ?: return
        sight.x = entity.x
        sight.z = entity.z
        sight.y = when (sight.kind) {
            ITEM -> entity.y + ITEM_LIFT
            else -> eyeY(entity)
        }
    }

    //? if > 1.8.9 {
    private fun entities(level: ClientLevel, area: AABB): List<Entity> = level.getEntities(null as Entity?, area)

    private fun gone(entity: Entity): Boolean = entity.isRemoved

    private fun eyeY(entity: Entity): Double = entity.eyeY
    //?} else {
    /*private fun entities(level: ClientLevel, area: AABB): List<Entity> = level.getEntities(null as Entity?, area)

    private fun gone(entity: Entity): Boolean = entity.removed

    private fun eyeY(entity: Entity): Double = entity.y + entity.eyeHeight
    *///?}
}
