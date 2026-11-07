package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.decoration.HangingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.vehicle.VehicleEntity
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
//? if >= 1.21.4
import net.minecraft.world.entity.EntitySpawnReason
//? if >= 1.21.8 {
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
//?}

object LobbyEntities {
    const val KEY = "entities"
    private const val RADIUS = 32.0
    private const val MAX_COUNT = 48
    private const val CLEARANCE = 1.5
    private const val DATA = "data"
    private const val HEAD = "head"
    private const val BODY = "body"
    private const val OWNER = "Owner"

    private val DROPPED = listOf(
        "UUID", "Passengers", "Motion", "Fire", "HurtTime", "HurtByTimestamp", "DeathTime", "Brain", "Offers", "Gossips",
        "Items", "Inventory", "leash", "Leash", "FallDistance", "fall_distance", "PortalCooldown", "Air", "Thrower",
        "LoveCause", "Trusted", "AngryAt", "angry_at", "anger_end_time", "Tags",
    )

    private val SERVER_DROPPED = listOf("CustomName", "CustomNameVisible")

    private val RENDERED_COMPONENTS = setOf(
        "minecraft:dyed_color", "minecraft:enchantment_glint_override", "minecraft:enchantments", "minecraft:stored_enchantments",
        "minecraft:trim", "minecraft:banner_patterns", "minecraft:base_color", "minecraft:profile", "minecraft:custom_model_data",
        "minecraft:item_model", "minecraft:potion_contents", "minecraft:map_color", "minecraft:firework_explosion",
        "minecraft:block_state", "minecraft:pot_decorations", "minecraft:charged_projectiles", "minecraft:equippable",
    )

    private val STAND_IN_OWNER = intArrayOf(0, 0, 0, 1)
    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
    private val reported = HashSet<String>()
    private const val FIRST_PROP_ID = 0x50524F50
    private var nextPropId = FIRST_PROP_ID

    fun gather(level: ClientLevel, anchor: BlockPos, minSection: Int, topSection: Int, privacy: LobbyPrivacy): ArrayDeque<Entity> {
        val centerX = anchor.x + 0.5
        val centerZ = anchor.z + 0.5
        val minY = SectionPos.sectionToBlockCoord(minSection)
        val maxY = SectionPos.sectionToBlockCoord(topSection + 1)
        val found = ArrayList<Entity>()
        for (entity in level.entitiesForRendering()) {
            if (!kept(entity, privacy)) continue
            val dx = entity.x - centerX
            val dz = entity.z - centerZ
            if (dx * dx + dz * dz > RADIUS * RADIUS || entity.y < minY || entity.y >= maxY) continue
            found += entity
        }
        found.sortBy { (it.x - centerX) * (it.x - centerX) + (it.z - centerZ) * (it.z - centerZ) }
        return ArrayDeque(found.take(MAX_COUNT))
    }

    fun save(level: ClientLevel, entity: Entity, privacy: LobbyPrivacy): CompoundTag? {
        if (!kept(entity, privacy)) return null
        val data = runCatching { write(level, entity) }.getOrNull() ?: return null
        DROPPED.forEach(data::remove)
        if (data.contains(OWNER)) data.put(OWNER, IntArrayTag(STAND_IN_OWNER.clone()))
        if (privacy == LobbyPrivacy.SERVER) SERVER_DROPPED.forEach(data::remove)
        scrub(data)
        return CompoundTag().apply {
            put(DATA, data)
            if (entity is LivingEntity) {
                putFloat(HEAD, entity.yHeadRot)
                putFloat(BODY, entity.yBodyRot)
            }
        }
    }

    fun load(level: ClientLevel, root: CompoundTag, stage: LobbyStage): List<Entity> {
        val list = root.getListOrEmpty(KEY)
        if (list.isEmpty() || !LobbySnapshot.native(root)) return emptyList()
        val camera = stage.camera()
        val loaded = ArrayList<Entity>()
        for (tag in list.compounds()) {
            if (loaded.size >= MAX_COUNT) break
            val entity = runCatching { create(level, tag.getCompoundOrEmpty(DATA)) }.getOrNull() ?: continue
            if (inTheWay(entity, stage, camera)) continue
            pose(entity, tag)
            loaded += entity
        }
        return loaded
    }

    fun enter(level: ClientLevel, entity: Entity) {
        entity.id = nextPropId++
        //? if >= 26.1
        if (!renders(entity, 1f)) return
        level.addEntity(entity)
        if (entity is Display) runCatching { level.tickNonPassenger(entity) }
    }

    fun tick(level: ClientLevel) {
        for (entity in level.entitiesForRendering().toList()) {
            if (entity.isRemoved || entity.isPassenger || level.tickRateManager().isEntityFrozen(entity)) continue
            //? if >= 26.1
            if (!level.isTickingEntity(entity)) continue
            try {
                level.tickNonPassenger(entity)
            } catch (error: Throwable) {
                drop(level, entity, "tick", error)
            }
        }
    }

    //? if >= 26.1 {
    fun purge(level: ClientLevel, partialTicks: Float): Boolean {
        var dropped = false
        for (entity in level.entitiesForRendering().toList()) {
            if (entity is LobbyAvatar || entity.isRemoved || renders(entity, partialTicks)) continue
            level.removeEntity(entity.id, Entity.RemovalReason.DISCARDED)
            dropped = true
        }
        return dropped
    }

    private fun renders(entity: Entity, partialTicks: Float): Boolean = try {
        Minecraft.getInstance().entityRenderDispatcher.extractEntity(entity, partialTicks)
        true
    } catch (error: Throwable) {
        report(entity, "render", error)
        false
    }
    //?}

    private fun drop(level: ClientLevel, entity: Entity, phase: String, error: Throwable) {
        report(entity, phase, error)
        if (entity !is LobbyAvatar) level.removeEntity(entity.id, Entity.RemovalReason.DISCARDED)
    }

    private fun report(entity: Entity, phase: String, error: Throwable) {
        val key = "$phase ${EntityType.getKey(entity.type)}"
        if (reported.add(key)) LOGGER.warn("Lobby entity {} failed during {}", EntityType.getKey(entity.type), phase, error)
    }

    private fun kept(entity: Entity, privacy: LobbyPrivacy): Boolean {
        if (entity.isRemoved || entity.isPassenger || !entity.type.canSerialize()) return false
        return when (entity) {
            is Display.TextDisplay -> privacy == LobbyPrivacy.OWN_WORLD
            is Mob, is ArmorStand, is HangingEntity, is Display, is VehicleEntity, is ItemEntity -> true
            else -> false
        }
    }

    private fun scrub(tag: CompoundTag) {
        for (key in tag.keyNames().toList()) {
            val child = tag.get(key)
            if (child is CompoundTag) {
                if (key == "components") {
                    child.keyNames().filterNot { it in RENDERED_COMPONENTS }.forEach(child::remove)
                } else {
                    scrub(child)
                }
            } else if (child is ListTag) {
                child.compounds().forEach(::scrub)
            }
        }
    }

    private fun inTheWay(entity: Entity, stage: LobbyStage, camera: Vec3): Boolean {
        val box = entity.boundingBox.inflate(CLEARANCE)
        if (box.contains(camera) || box.clip(camera, stage.aim).isPresent) return true
        return stage.slots.any { box.contains(it.pos) } || stage.background.any { box.contains(it.pos) }
    }

    private fun pose(entity: Entity, tag: CompoundTag) {
        entity.isSilent = true
        if (entity is LivingEntity) {
            val head = tag.getFloatOr(HEAD, entity.yRot)
            val body = tag.getFloatOr(BODY, entity.yRot)
            entity.yHeadRot = head
            entity.yHeadRotO = head
            entity.yBodyRot = body
            entity.yBodyRotO = body
        }
        entity.setOldPosAndRot()
    }

    private fun write(level: ClientLevel, entity: Entity): CompoundTag? {
        //? if >= 1.21.8 {
        val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess())
        return if (entity.saveAsPassenger(output)) output.buildResult() else null
        //?} else {
        /*val tag = CompoundTag()
        return if (entity.saveAsPassenger(tag)) tag else null
        *///?}
    }

    private fun create(level: ClientLevel, data: CompoundTag): Entity? {
        //? if >= 1.21.10 {
        val input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data)
        val type = EntityType.by(input).orElse(null) ?: return null
        return EntityType.create(type, input, level, EntitySpawnReason.LOAD).orElse(null)
        //?} elif >= 1.21.8 {
        /*return EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data), level, EntitySpawnReason.LOAD).orElse(null)
        *///?} elif >= 1.21.4 {
        /*return EntityType.create(data, level, EntitySpawnReason.LOAD).orElse(null)
        *///?} else {
        /*return EntityType.create(data, level).orElse(null)
        *///?}
    }
}
//?}
