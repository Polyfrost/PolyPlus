package org.polyfrost.polyplus.client.ping

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import kotlin.math.max

//? if > 1.8.9 {
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.vehicle.ContainerEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
//?} else {
/*import net.minecraft.block.Block
import net.minecraft.entity.Entities
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.living.LivingEntity
import net.minecraft.entity.living.mob.monster.Monster
import net.minecraft.entity.living.player.PlayerEntity
import net.minecraft.entity.vehicle.InventoryMinecartEntity
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.HitResult
*///?}

internal class PingHit(
    val kind: PingKind,
    val x: Double,
    val y: Double,
    val z: Double,
    val target: PingTarget?,
    val label: String?,
)

internal object PingTargeting {
    private const val MIN_REACH = 64.0
    private const val ENTITY_MARGIN = 0.3
    private const val LABEL_LIFT = 0.25

    private val DANGER_BLOCKS = setOf(
        "lava", "flowing_lava", "fire", "soul_fire", "cactus", "magma_block", "tnt", "powder_snow",
        "sweet_berry_bush", "wither_rose", "campfire", "soul_campfire", "pointed_dripstone",
    )
    private val CONTAINER_BLOCKS = setOf(
        "chest", "trapped_chest", "barrel", "ender_chest", "furnace", "lit_furnace", "blast_furnace", "smoker",
        "hopper", "dispenser", "dropper", "brewing_stand", "crafter", "decorated_pot", "chiseled_bookshelf",
    )
    private val ORE_BLOCKS = setOf("ancient_debris")

    private fun isContainer(path: String) = path in CONTAINER_BLOCKS || path.endsWith("shulker_box")

    private fun isOre(path: String) = path.endsWith("_ore") || path in ORE_BLOCKS

    fun reach(): Double {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val chunks = mc.options.effectiveRenderDistance
        //?} else {
        /*val chunks = mc.options.viewDistance
        *///?}
        return max(MIN_REACH, chunks * 16.0)
    }

    private fun blockHit(id: String, label: String, hitX: Double, hitY: Double, hitZ: Double, bx: Int, by: Int, bz: Int, top: Boolean): PingHit {
        val path = id.substringAfter(':')
        val target = PingTarget(PingTargetType.BLOCK, id)
        return when {
            path in DANGER_BLOCKS -> PingHit(PingKind.DANGER, hitX, hitY, hitZ, target, label)
            isContainer(path) || isOre(path) -> PingHit(PingKind.LOOT, bx + 0.5, by + 0.5, bz + 0.5, target, label)
            top -> PingHit(PingKind.GO, bx + 0.5, hitY, bz + 0.5, target, label)
            else -> PingHit(PingKind.GO, hitX, hitY, hitZ, target, label)
        }
    }

    //? if > 1.8.9 {
    fun raycast(partialTicks: Float): PingHit? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val camera = mc.cameraEntity ?: mc.player ?: return null
        val reach = reach()
        val eye = camera.getEyePosition(partialTicks)
        val look = camera.getViewVector(partialTicks)
        val end = eye.add(look.x * reach, look.y * reach, look.z * reach)

        (mc.hitResult as? EntityHitResult)?.let { vanilla ->
            val entity = vanilla.entity
            if (pingable(entity, camera)) return entityHit(entity, vanilla.location)
        }

        val blocks = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, camera))
        val blockMissed = blocks.type == net.minecraft.world.phys.HitResult.Type.MISS
        var nearest = if (blockMissed) reach * reach else blocks.location.distanceToSqr(eye)
        var picked: Entity? = null
        var pickedAt = eye

        val area = camera.boundingBox.expandTowards(look.x * reach, look.y * reach, look.z * reach).inflate(1.0)
        val candidates = level.getEntities(camera, area) { pingable(it, camera) }
        for (entity in candidates) {
            val box = entity.boundingBox.inflate(max(entity.pickRadius.toDouble(), ENTITY_MARGIN))
            val at = if (box.contains(eye)) eye else box.clip(eye, end).orElse(null) ?: continue
            val distance = at.distanceToSqr(eye)
            if (distance < nearest) {
                nearest = distance
                picked = entity
                pickedAt = at
            }
        }

        picked?.let { return entityHit(it, pickedAt) }
        if (blockMissed) return null
        val pos = blocks.blockPos
        val state = level.getBlockState(pos)
        val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        val at = blocks.location
        return blockHit(id, state.block.name.string, at.x, at.y, at.z, pos.x, pos.y, pos.z, blocks.direction == Direction.UP)
    }

    private fun pingable(entity: Entity, camera: Entity): Boolean {
        val mc = Minecraft.getInstance()
        if (entity === camera || entity === mc.player || entity.isRemoved) return false
        if (entity.isSpectator || entity.isInvisible || entity is Projectile) return false
        return entity.isPickable || entity is ItemEntity
    }

    private fun entityHit(entity: Entity, at: Vec3): PingHit {
        val uuid = entity.uuid.toString()
        val x = entity.x
        val y = entity.y + entity.bbHeight + LABEL_LIFT
        val z = entity.z
        val typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
        val name = entity.name.string
        return when (entity) {
            is ItemEntity -> {
                val stack = entity.item
                val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                PingHit(PingKind.LOOT, x, y, z, PingTarget(PingTargetType.ITEM, id, uuid), stack.hoverName.string)
            }
            is Player -> PingHit(PingKind.LOOK, x, y, z, PingTarget(PingTargetType.PLAYER, null, uuid), name)
            is Enemy -> PingHit(PingKind.ENEMY, x, y, z, PingTarget(PingTargetType.ENTITY, typeId, uuid), name)
            is ContainerEntity -> PingHit(PingKind.LOOT, x, y, z, PingTarget(PingTargetType.ENTITY, typeId, uuid), name)
            is LivingEntity -> PingHit(PingKind.LOOK, x, y, z, PingTarget(PingTargetType.ENTITY, typeId, uuid), name)
            else -> PingHit(PingKind.LOOK, x, max(y, at.y), z, PingTarget(PingTargetType.ENTITY, typeId, uuid), name)
        }
    }
    //?} else {
    /*fun raycast(partialTicks: Float): PingHit? {
        val mc = Minecraft.getInstance()
        val world = mc.world ?: return null
        val camera = mc.camera ?: mc.player ?: return null
        val reach = reach()
        val eye = camera.getEyePosition(partialTicks)
        val look = camera.getRotationVector(partialTicks)
        val end = eye.add(look.x * reach, look.y * reach, look.z * reach)

        mc.crosshairTarget?.takeIf { it.type == HitResult.Type.ENTITY }?.let { vanilla ->
            val entity = vanilla.entity
            if (entity != null && pingable(entity, camera)) return entityHit(entity, vanilla.facePos ?: eye)
        }

        val blocks = world.rayTrace(eye, end, true, false, false)
        val blockFound = blocks != null && blocks.type == HitResult.Type.BLOCK
        var nearest = if (blockFound) blocks!!.facePos.squaredDistanceTo(eye) else reach * reach
        var picked: Entity? = null
        var pickedAt = eye

        val area = camera.shape.expanded(look.x * reach, look.y * reach, look.z * reach).grown(1.0, 1.0, 1.0)
        for (entity in world.getEntities(camera, area)) {
            if (!pingable(entity, camera)) continue
            val margin = max(entity.pickRadius.toDouble(), ENTITY_MARGIN)
            val box = entity.shape.grown(margin, margin, margin)
            val at = if (box.contains(eye)) eye else box.clip(eye, end)?.facePos ?: continue
            val distance = at.squaredDistanceTo(eye)
            if (distance < nearest) {
                nearest = distance
                picked = entity
                pickedAt = at
            }
        }

        picked?.let { return entityHit(it, pickedAt) }
        if (!blockFound) return null
        val pos = blocks!!.pos
        val block = world.getBlockState(pos).block
        val id = Block.REGISTRY.getKey(block)?.toString() ?: return null
        val at = blocks.facePos
        return blockHit(id, block.name, at.x, at.y, at.z, pos.x, pos.y, pos.z, blocks.face == Direction.UP)
    }

    private fun pingable(entity: Entity, camera: Entity): Boolean {
        val mc = Minecraft.getInstance()
        if (entity === camera || entity === mc.player || entity.removed) return false
        if (entity is PlayerEntity && entity.isSpectator) return false
        if (entity.isInvisible) return false
        return entity.hasCollision() || entity is ItemEntity
    }

    private fun entityHit(entity: Entity, at: Vec3d): PingHit {
        val uuid = entity.uuid.toString()
        val x = entity.x
        val y = entity.y + entity.height + LABEL_LIFT
        val z = entity.z
        val typeId = Entities.getKey(entity)
        val name = entity.name
        return when (entity) {
            is ItemEntity -> PingHit(PingKind.LOOT, x, y, z, PingTarget(PingTargetType.ITEM, null, uuid), entity.item?.hoverName)
            is PlayerEntity -> PingHit(PingKind.LOOK, x, y, z, PingTarget(PingTargetType.PLAYER, null, uuid), name)
            is Monster -> PingHit(PingKind.ENEMY, x, y, z, PingTarget(PingTargetType.ENTITY, typeId, uuid), name)
            is InventoryMinecartEntity -> PingHit(PingKind.LOOT, x, y, z, PingTarget(PingTargetType.ENTITY, typeId, uuid), name)
            is LivingEntity -> PingHit(PingKind.LOOK, x, y, z, PingTarget(PingTargetType.ENTITY, typeId, uuid), name)
            else -> PingHit(PingKind.LOOK, x, max(y, at.y), z, PingTarget(PingTargetType.ENTITY, typeId, uuid), name)
        }
    }
    *///?}
}
