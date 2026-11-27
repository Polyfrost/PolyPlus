package org.polyfrost.polyplus.client.cosmetics.signals

import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.phys.AABB
//?}

//? if >= 1.21.4 {
import net.minecraft.world.entity.Entity
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
//?}

//? if > 1.8.9 && < 1.21.8 {
/*import net.minecraft.world.level.levelgen.Heightmap
*///?}

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.living.LivingEntity
import net.minecraft.entity.living.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.polyfrost.polyplus.mixin.client.signals.SignalsTimerAccessor
*///?}

object PlayerSignals {
    const val IS_SNEAKING = 0
    const val IS_SPRINTING = 1
    const val IS_SWIMMING = 2
    const val IS_IN_WATER = 3
    const val IS_IN_RAIN = 4
    const val IS_IN_SNOW = 5
    const val IS_ON_FIRE = 6
    const val IS_HURT = 7
    const val IS_GLIDING = 8
    const val IS_FLYING = 9
    const val IS_FALLING = 10
    const val IS_ON_GROUND = 11
    const val IS_RIDING = 12
    const val IS_SLEEPING = 13
    const val IS_EMOTING = 14
    const val IS_IN_COMBAT = 15
    const val IS_MOVING = 16
    const val GROUND_SPEED = 17
    const val VERTICAL_SPEED = 18
    const val TIME_OF_DAY = 19
    const val BIOME_TEMPERATURE = 20
    const val IS_IN_OCEAN = 21
    const val DIMENSION = 22
    const val SIGNAL_COUNT = 23

    const val YAW_SPEED = 23
    const val QUERY_COUNT = 24

    const val EVENT_HURT = 1
    const val EVENT_ATTACK_SWING = 2
    const val EVENT_DEATH = 4
    const val EVENT_RESPAWN = 8
    const val EVENT_DIMENSION_CHANGE = 16
    const val EVENT_SLEEP = 32
    const val EVENT_JUMP = 64

    const val READER_COSMETICS = 0
    const val READER_NAMEPLATES = 1
    const val READER_PETS = 2
    const val READER_HANDS = 3
    const val READERS = 4

    internal const val TICKS_PER_SECOND = 20.0

    private const val REACH = 4.5
    private const val CONE_COS = 0.8660254037844387
    private const val EPSILON = 1.0e-6
    private const val FLYING_AIR_TICKS = 10
    private const val FALLING_AIR_TICKS = 4
    private const val HOVER_SPEED = 0.1f
    private const val FALL_SPEED = -0.5f
    private const val MOVING_SPEED = 0.05f
    private const val DAY_TICKS = 24000L
    private const val NEUTRAL_TIME_OF_DAY = 0.25f
    private const val NEUTRAL_TEMPERATURE = 0.8f
    private const val PRUNE_MILLIS = 10_000L
    private const val STALE_MILLIS = 60_000L

    @JvmField
    val SIGNAL_NAMES: Array<String> = arrayOf(
        "is_sneaking", "is_sprinting", "is_swimming", "is_in_water", "is_in_rain", "is_in_snow",
        "is_on_fire", "is_hurt", "is_gliding", "is_flying", "is_falling", "is_on_ground",
        "is_riding", "is_sleeping", "is_emoting", "is_in_combat", "is_moving", "ground_speed",
        "vertical_speed", "time_of_day", "biome_temperature", "is_in_ocean", "dimension",
    )

    @JvmField
    val QUERY_NAMES: Array<String> = SIGNAL_NAMES + arrayOf("yaw_speed")

    private val queryIndices: Map<String, Int> = HashMap<String, Int>().apply {
        QUERY_NAMES.forEachIndexed { index, name -> put(name, index) }
    }

    @JvmField
    val NEUTRAL: PlayerSignalRecord = PlayerSignalRecord().apply {
        set(IS_ON_GROUND, true)
        set(TIME_OF_DAY, NEUTRAL_TIME_OF_DAY)
        set(BIOME_TEMPERATURE, NEUTRAL_TEMPERATURE)
    }

    private val records = HashMap<UUID, PlayerSignalRecord>()
    private var lastPrune = 0L

    init {
        eventHandler<TickEvent.End> { tickJumps() }
    }

    fun queryIndex(name: String): Int = queryIndices[name] ?: -1

    @JvmStatic
    fun of(state: Any?): PlayerSignalRecord = (state as? PlayerSignalsRenderAccess)?.`polyplus$signals`() ?: NEUTRAL

    private fun record(id: UUID): PlayerSignalRecord {
        val now = System.currentTimeMillis()
        if (now - lastPrune > PRUNE_MILLIS) {
            lastPrune = now
            records.values.removeIf { now - it.seen > STALE_MILLIS }
        }
        val record = records.getOrPut(id) { PlayerSignalRecord() }
        record.seen = now
        return record
    }

    private fun queueSwing(id: UUID) {
        record(id).raise(EVENT_ATTACK_SWING)
    }

    private fun emoting(player: Any): Boolean =
        (player as? PlayerEmotesAccess)?.`polyplus$emoteController`()?.isActive == true

    private fun finish(
        record: PlayerSignalRecord,
        elapsed: Int,
        onGround: Boolean,
        airborne: Boolean,
        localFlying: Boolean?,
        gliding: Boolean,
        riding: Boolean,
    ) {
        record.airborne(airborne, elapsed)
        val vertical = record.verticalSpeed
        val flying = localFlying
            ?: (record.airTicks >= FLYING_AIR_TICKS && !gliding && !riding && abs(vertical) <= HOVER_SPEED)
        val falling = record.airTicks >= FALLING_AIR_TICKS && vertical < FALL_SPEED && !gliding && !flying
        record.set(IS_ON_GROUND, onGround)
        record.set(IS_RIDING, riding)
        record.set(IS_GLIDING, gliding)
        record.set(IS_FLYING, flying)
        record.set(IS_FALLING, falling)
        record.set(IS_IN_COMBAT, false)
        record.set(IS_MOVING, record.groundSpeed > MOVING_SPEED)
    }

    private fun dayFraction(time: Long): Float = Math.floorMod(time, DAY_TICKS).toFloat() / DAY_TICKS

    private fun inCone(
        eyeX: Double, eyeY: Double, eyeZ: Double,
        yawDegrees: Float, pitchDegrees: Float,
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
    ): Boolean {
        val nearX = eyeX.coerceIn(minX, maxX) - eyeX
        val nearY = eyeY.coerceIn(minY, maxY) - eyeY
        val nearZ = eyeZ.coerceIn(minZ, maxZ) - eyeZ
        val near = nearX * nearX + nearY * nearY + nearZ * nearZ
        if (near > REACH * REACH) return false
        if (near < EPSILON) return true
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val lookX = -sin(yaw) * cos(pitch)
        val lookY = -sin(pitch)
        val lookZ = cos(yaw) * cos(pitch)
        return facing(lookX, lookY, lookZ, nearX, nearY, nearZ) ||
            facing(lookX, lookY, lookZ, (minX + maxX) / 2 - eyeX, (minY + maxY) / 2 - eyeY, (minZ + maxZ) / 2 - eyeZ)
    }

    private fun facing(lookX: Double, lookY: Double, lookZ: Double, x: Double, y: Double, z: Double): Boolean {
        val length = sqrt(x * x + y * y + z * z)
        return length < EPSILON || (lookX * x + lookY * y + lookZ * z) / length >= CONE_COS
    }

    //? if >= 1.21.4 {
    @JvmStatic
    fun extract(entity: Entity, state: PlayerSignalsRenderAccess, partialTick: Float) {
        state.`polyplus$setSignals`(PreviewTryOn.lobbySignals(entity) ?: if (entity is Player) sample(entity, partialTick) else NEUTRAL)
    }
    //?}

    //? if = 1.21.1 {
    /*@JvmStatic
    fun sample(player: Player): PlayerSignalRecord =
        sample(player, Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false))
    *///?}

    //? if > 1.8.9 {
    private val OCEANS: Array<ResourceKey<Biome>> = arrayOf(
        Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN,
        Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN,
    )

    @JvmStatic
    fun sample(player: Player, partialTick: Float): PlayerSignalRecord {
        val level = Minecraft.getInstance().level ?: return NEUTRAL
        if (player.level() !== level || level.getEntity(player.id) !== player) return NEUTRAL
        val record = record(player.uuid)
        if (!record.isTracking(player, player.tickCount)) tick(record, player, level)
        record.interpolate(partialTick)
        return record
    }

    @JvmStatic
    fun onSwingStart(entity: LivingEntity) {
        if (entity !is Player) return
        val level = Minecraft.getInstance().level ?: return
        if (entity.level() !== level || !hasTargetInCone(entity, level)) return
        queueSwing(entity.uuid)
    }

    private fun tickJumps() {
        if (records.isEmpty()) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        for (player in level.players()) {
            val record = records[player.uuid] ?: continue
            val flying = player === mc.player && player.abilities.flying
            val free = !player.isInWater && !player.isPassenger && !player.isInLava && !player.onClimbable() && !player.isFallFlying && !flying
            record.takeoff(player, player.tickCount, player.y, player.onGround(), free)
        }
    }

    private fun tick(record: PlayerSignalRecord, player: Player, level: ClientLevel) {
        val elapsed = record.begin(player, level, player.tickCount, player.x, player.y, player.z, player.yRot)
        record.edges(player.hurtTime, player.deathTime, player.isSleeping)
        val feet = player.blockPosition()
        val head = BlockPos.containing(player.x, player.boundingBox.maxY, player.z)
        val biome = level.getBiome(feet)
        val inWater = player.isInWater
        val riding = player.isPassenger
        val onGround = player.onGround()
        record.set(IS_SNEAKING, player.isCrouching)
        record.set(IS_SPRINTING, player.isSprinting)
        record.set(IS_SWIMMING, player.isSwimming)
        record.set(IS_IN_WATER, inWater)
        record.set(IS_IN_RAIN, level.isRainingAt(feet) || level.isRainingAt(head))
        record.set(IS_IN_SNOW, snowsAt(level, head))
        record.set(IS_ON_FIRE, player.displayFireAnimation())
        record.set(IS_HURT, player.hurtTime > 0)
        record.set(IS_SLEEPING, player.isSleeping)
        record.set(IS_EMOTING, emoting(player))
        record.set(BIOME_TEMPERATURE, biome.value().baseTemperature)
        record.set(IS_IN_OCEAN, OCEANS.any { biome.`is`(it) })
        record.set(DIMENSION, dimension(level).toFloat())
        record.timeOfDay(dayFraction(dayTime(level)), elapsed)
        val airborne = !onGround && !inWater && !riding && !player.isInLava && !player.onClimbable()
        val localFlying = if (player === Minecraft.getInstance().player) player.abilities.flying else null
        finish(record, elapsed, onGround, airborne, localFlying, player.isFallFlying, riding)
    }

    private fun dimension(level: ClientLevel): Int = when (level.dimension()) {
        Level.OVERWORLD -> 0
        Level.NETHER -> 1
        Level.END -> 2
        else -> -1
    }

    private fun dayTime(level: ClientLevel): Long {
        //? if >= 26.1 {
        return level.defaultClockTime
        //?} else {
        /*return level.dayTime
        *///?}
    }

    private fun snowsAt(level: ClientLevel, pos: BlockPos): Boolean {
        //? if >= 1.21.8 {
        return level.precipitationAt(pos) == Biome.Precipitation.SNOW
        //?} elif >= 1.21.4 {
        /*if (!level.isRaining || !level.canSeeSky(pos)) return false
        if (level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).y > pos.y) return false
        return level.getBiome(pos).value().getPrecipitationAt(pos, level.seaLevel) == Biome.Precipitation.SNOW
        *///?} else {
        /*if (!level.isRaining || !level.canSeeSky(pos)) return false
        if (level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).y > pos.y) return false
        return level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW
        *///?}
    }

    private fun hasTargetInCone(player: Player, level: ClientLevel): Boolean {
        val eyeX = player.x
        val eyeY = player.eyeY
        val eyeZ = player.z
        val area = AABB(eyeX - REACH, eyeY - REACH, eyeZ - REACH, eyeX + REACH, eyeY + REACH, eyeZ + REACH)
        for (target in level.getEntitiesOfClass(LivingEntity::class.java, area)) {
            if (target === player || target.isRemoved || target.isSpectator || target.deathTime > 0) continue
            if (player.hasPassenger(target) || player.vehicle === target) continue
            val box = target.boundingBox
            if (inCone(eyeX, eyeY, eyeZ, player.yHeadRot, player.xRot, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                return true
            }
        }
        return false
    }
    //?} else {
    /*private const val SNOW_TEMPERATURE = 0.15f
    private val OCEAN_BIOME_IDS = intArrayOf(0, 10, 24)

    @JvmStatic
    fun sample(player: PlayerEntity): PlayerSignalRecord =
        sample(player, (Minecraft.getInstance() as SignalsTimerAccessor).`polyplus$signalTimer`().partialTick)

    @JvmStatic
    fun sample(player: PlayerEntity, partialTick: Float): PlayerSignalRecord {
        val world = Minecraft.getInstance().world ?: return NEUTRAL
        if (player.world !== world || world.getEntity(player.networkId) !== player) return NEUTRAL
        val record = record(player.uuid)
        if (!record.isTracking(player, player.ticks)) tick(record, player, world)
        record.interpolate(partialTick)
        return record
    }

    @JvmStatic
    fun onSwingStart(entity: LivingEntity) {
        if (entity !is PlayerEntity) return
        val world = Minecraft.getInstance().world ?: return
        if (entity.world !== world || !hasTargetInCone(entity, world)) return
        queueSwing(entity.uuid)
    }

    private fun tickJumps() {
        if (records.isEmpty()) return
        val mc = Minecraft.getInstance()
        val world = mc.world ?: return
        for (player in world.players) {
            val record = records[player.uuid] ?: continue
            val flying = player === mc.player && player.abilities.flying
            val free = !player.isInWater && !player.isRiding && !player.isInLava && !player.isClimbing && !flying
            record.takeoff(player, player.ticks, player.y, player.onGround, free)
        }
    }

    private fun tick(record: PlayerSignalRecord, player: PlayerEntity, world: ClientWorld) {
        val elapsed = record.begin(player, world, player.ticks, player.x, player.y, player.z, player.yaw)
        record.edges(player.damagedTimer, player.deathTicks, player.isSleeping)
        val feet = BlockPos(player.x, player.y, player.z)
        val head = BlockPos(player.x, player.shape.maxY, player.z)
        val biome = world.getBiome(feet)
        val inWater = player.isInWater
        val riding = player.isRiding
        val onGround = player.onGround
        record.set(IS_SNEAKING, player.isSneaking)
        record.set(IS_SPRINTING, player.isSprinting)
        record.set(IS_SWIMMING, inWater && !onGround)
        record.set(IS_IN_WATER, inWater)
        record.set(IS_IN_RAIN, world.isRaining(feet) || world.isRaining(head))
        record.set(IS_IN_SNOW, snowsAt(world, head))
        record.set(IS_ON_FIRE, player.isOnFire)
        record.set(IS_HURT, player.damagedTimer > 0)
        record.set(IS_SLEEPING, player.isSleeping)
        record.set(IS_EMOTING, emoting(player))
        record.set(BIOME_TEMPERATURE, biome.temperature)
        record.set(IS_IN_OCEAN, biome.id in OCEAN_BIOME_IDS)
        record.set(DIMENSION, dimension(world.dimension.id).toFloat())
        record.timeOfDay(dayFraction(world.timeOfDay), elapsed)
        val airborne = !onGround && !inWater && !riding && !player.isInLava && !player.isClimbing
        val localFlying = if (player === Minecraft.getInstance().player) player.abilities.flying else null
        finish(record, elapsed, onGround, airborne, localFlying, false, riding)
    }

    private fun dimension(id: Int): Int = when (id) {
        0 -> 0
        -1 -> 1
        1 -> 2
        else -> -1
    }

    private fun snowsAt(world: ClientWorld, pos: BlockPos): Boolean {
        if (!world.isRaining || !world.hasSkyAccess(pos) || world.getPrecipitationHeight(pos).y > pos.y) return false
        val biome = world.getBiome(pos)
        return (biome.isRainy || biome.isSnowy) && biome.getTemperature(pos) < SNOW_TEMPERATURE
    }

    private fun hasTargetInCone(player: PlayerEntity, world: ClientWorld): Boolean {
        val eyeX = player.x
        val eyeY = player.y + player.eyeHeight
        val eyeZ = player.z
        val area = Box(eyeX - REACH, eyeY - REACH, eyeZ - REACH, eyeX + REACH, eyeY + REACH, eyeZ + REACH)
        for (target in world.getEntitiesOfType(LivingEntity::class.java, area)) {
            if (target === player || target.removed || target.deathTicks > 0) continue
            if (target is PlayerEntity && target.isSpectator) continue
            if (player.rider === target || player.vehicle === target) continue
            val box = target.shape
            if (inCone(eyeX, eyeY, eyeZ, player.headYaw, player.pitch, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                return true
            }
        }
        return false
    }
    *///?}
}
