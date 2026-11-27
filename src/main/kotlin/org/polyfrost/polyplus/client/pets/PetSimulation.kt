package org.polyfrost.polyplus.client.pets

import net.minecraft.client.multiplayer.ClientLevel
import org.polyfrost.polyplus.client.cosmetics.render.WearerJumps
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class PetOwner {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var bodyYaw = 0f
    var renderX = 0.0
    var renderY = 0.0
    var renderZ = 0.0
    var renderBodyYaw = 0f
    var renderHeadYaw = 0f
    var renderPitch = 0f
    var limbSwing = 0f
    var limbAmount = 0f
    var sleepYaw = Float.NaN
    var sleepShiftX = 0f
    var sleepShiftY = 0f
    var sleepShiftZ = 0f
    var leanPitch = 0f
    var leanYaw = 0f
    var swimShift = false
    var eyeHeight = 1.62
    var leftHanded = false
    var signals: PlayerSignalRecord = PlayerSignals.NEUTRAL
    var events = 0
    var idleSince = Double.NaN
    var combatUntil = Double.NEGATIVE_INFINITY
    var sampleTick = 0L

    fun flag(signal: Int): Boolean = signals.flag(signal)

    fun upright() {
        sleepYaw = Float.NaN
        sleepShiftX = 0f
        sleepShiftY = 0f
        sleepShiftZ = 0f
        leanPitch = 0f
        leanYaw = 0f
        swimShift = false
    }

    fun inCombat(now: Double): Boolean = now < combatUntil || flag(PlayerSignals.IS_IN_COMBAT)

    fun observe(now: Double) {
        sampleTick = floor(now / PetSimulation.TICK_SECONDS + SAMPLE_EPSILON).toLong()
        val raised = signals.events(PlayerSignals.READER_PETS)
        events = events or raised
        if (raised and COMBAT_ENTER != 0) combatUntil = now + COMBAT_SECONDS
        if (raised and COMBAT_EXIT != 0) combatUntil = Double.NEGATIVE_INFINITY
        if (flag(PlayerSignals.IS_MOVING) || idleSince.isNaN()) idleSince = now
    }

    fun takeEvents(): Int {
        val taken = events
        events = 0
        return taken
    }

    fun idleFor(now: Double): Double = if (idleSince.isNaN()) 0.0 else now - idleSince

    private companion object {
        const val COMBAT_SECONDS = 10.0
        const val SAMPLE_EPSILON = 1.0e-6
        const val COMBAT_ENTER = PlayerSignals.EVENT_HURT or PlayerSignals.EVENT_ATTACK_SWING
        const val COMBAT_EXIT = PlayerSignals.EVENT_DEATH or PlayerSignals.EVENT_RESPAWN or
            PlayerSignals.EVENT_DIMENSION_CHANGE or PlayerSignals.EVENT_SLEEP
    }
}

internal class PetDirective {
    var interaction: PetInteraction? = null
    var partner: PetManager.Pet? = null
    var role = 0
    var markX = 0.0
    var markY = 0.0
    var markZ = 0.0
    var faceYaw = 0f
    var circleX = 0.0
    var circleZ = 0.0
    var circleRadius = 0.0
    var circlePhase = 0.0
    var spin = 1.0
    var anchorX = 0.0
    var anchorY = 0.0
    var anchorZ = 0.0
    var anchorYaw = 0f
    var baseMarkX = 0.0
    var baseMarkY = 0.0
    var baseMarkZ = 0.0
    var startedAt = Double.NaN
    var startedClock = Double.NaN
    var authored = false

    val active: Boolean get() = interaction != null

    val started: Boolean get() = !startedAt.isNaN()

    fun clear() {
        interaction = null
        partner = null
        startedAt = Double.NaN
        startedClock = Double.NaN
        authored = false
    }
}

internal class PetBody(seed: Long) {
    var level: WeakReference<ClientLevel>? = null
    var spawned = false
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var prevX = 0.0
    var prevY = 0.0
    var prevZ = 0.0
    var vx = 0.0
    var vy = 0.0
    var vz = 0.0
    var yaw = 0f
    var prevYaw = 0f
    var pitch = 0f
    var prevPitch = 0f
    var headYaw = 0f
    var prevHeadYaw = 0f
    var headPitch = 0f
    var prevHeadPitch = 0f
    var moveYaw = 0f
    var onGround = false
    var inWater = false
    var ticks = 0
    var tickIndex = 0L
    var lastTick = 0.0
    var lastSeen = 0.0
    var mode = PetVariables.IDLE
    var action = 0f
    var speed = 0f
    var distance = 0f
    var visible = false
    var mood = 0f
    var arrived = false
    var limb = 0f
    var gait = false
    var headStable = 0
    var stableHeadYaw = 0f
    var appearAt = Double.NaN
    var unloadedSince = Double.NaN
    var footingAt = Int.MIN_VALUE
    var footingX = 0.0
    var footingZ = 0.0
    var footingClear = true
    val directive = PetDirective()
    val mind = PetMind(seed)
    val phase = ((seed ushr 11).toDouble() / (1L shl 53).toDouble()) * Math.PI * 2.0

    fun alpha(now: Double): Float = ((now - lastTick) / PetSimulation.TICK_SECONDS).toFloat().coerceIn(0f, 1f)

    fun renderX(alpha: Float): Double = prevX + (x - prevX) * alpha

    fun renderY(alpha: Float): Double = prevY + (y - prevY) * alpha

    fun renderZ(alpha: Float): Double = prevZ + (z - prevZ) * alpha

    fun renderYaw(alpha: Float): Float = prevYaw + PetSimulation.wrapDegrees(yaw - prevYaw) * alpha

    fun renderPitch(alpha: Float): Float = prevPitch + (pitch - prevPitch) * alpha

    fun lookYaw(alpha: Float): Float {
        val head = prevHeadYaw + PetSimulation.wrapDegrees(headYaw - prevHeadYaw) * alpha
        return PetSimulation.wrapDegrees(head - renderYaw(alpha))
    }

    fun lookPitch(alpha: Float): Float = prevHeadPitch + (headPitch - prevHeadPitch) * alpha

    fun appear(now: Double): Float {
        if (appearAt.isNaN()) return 1f
        val t = ((now - appearAt) / PetSimulation.APPEAR_SECONDS).toFloat()
        if (t >= 1f) {
            appearAt = Double.NaN
            return 1f
        }
        val eased = 1f - (1f - t.coerceAtLeast(0f)).pow(3)
        return PetSimulation.APPEAR_FROM + (1f - PetSimulation.APPEAR_FROM) * eased
    }

    fun cheer(amount: Float) {
        mood = (mood + amount).coerceIn(-1f, 1f)
    }

    fun place(x: Double, y: Double, z: Double, yaw: Float) {
        this.x = x
        this.y = y
        this.z = z
        prevX = x
        prevY = y
        prevZ = z
        this.yaw = yaw
        prevYaw = yaw
        headYaw = yaw
        prevHeadYaw = yaw
        headPitch = 0f
        prevHeadPitch = 0f
        pitch = 0f
        prevPitch = 0f
        limb = 0f
        gait = false
        headStable = 0
        stableHeadYaw = yaw
        vx = 0.0
        vy = 0.0
        vz = 0.0
    }
}

internal object PetSimulation {
    const val TICK_SECONDS = 0.05
    const val CLIMB_SLACK = 0.5
    const val APPEAR_SECONDS = 0.3
    const val APPEAR_FROM = 0.3f
    private const val MAX_CATCH_UP_TICKS = 5
    private const val TICK_EPSILON = 1.0e-6
    private const val ABSENT_RESPAWN_SECONDS = 1.0
    private const val SPAWN_BEHIND = 1.5
    private const val GRAVITY = 0.08
    private const val DRAG = 0.98
    private const val WATER_RISE = 0.04
    private const val WATER_DRAG = 0.8
    private const val WATER_SPEED = 0.5
    private const val TERMINAL = -3.92
    private const val STEP_HEIGHT = 0.6
    private const val JUMP_VELOCITY = 0.42
    private const val JUMP_REACH = 1.3
    private const val JUMP_CARRY = 0.15
    private const val GROUND_FRICTION = 0.546
    private const val AIR_FRICTION = 0.91
    private const val AIR_ACCEL = 0.006
    private const val FLY_ACCEL = 0.02
    private const val ARRIVE_GAIN = 0.3
    private const val ARRIVE_SHARE = 0.5
    private const val MIN_SPEED = 0.02
    private const val REST_SPEED = 0.003
    private const val MAX_TURN = 90f
    private const val FLY_TURN = 14f
    private const val TURN_EASE = 0.35f
    private const val FACE_TURN = 10f
    private const val HEAD_TURN = 10f
    private const val HEAD_PITCH_TURN = 40f
    private const val HEAD_YAW_LIMIT = 75f
    private const val HEAD_PITCH_LIMIT = 40f
    private const val BODY_FOLLOWS_HEAD = 50f
    private const val HEAD_STABLE_ANGLE = 15f
    private const val HEAD_STABLE_TICKS = 10
    private const val FACE_FORWARD_TICKS = 10f
    private const val STILL_SQR = 2.5e-7
    private const val LIMB_SCALE = 4f
    private const val LIMB_EASE = 0.4f
    private const val GAIT_ON = 0.25f
    private const val GAIT_OFF = 0.12f
    private const val BODY_WIDTH = 0.5
    private const val BODY_HEIGHT = 0.6
    private const val FLY_HEIGHT = 1.4
    private const val PITCH_EASE = 0.2f
    private const val MAX_PITCH_DEGREES = 35.0
    private const val PITCH_FLOOR = 0.15
    private const val WAYPOINT_REACH = 0.45
    private const val DIRECT_BLOCKS = 2.0
    private const val CLIMB_GAP = 1.0
    private const val REPATH_BLOCKS = 1.5
    private const val REPATH_SECONDS = 1.0
    private const val PATH_STEPS_PER_PET = 32
    private const val PATH_STEPS_PER_TICK = 64
    private const val FAR_PATH_STEPS_PER_PET = 8
    private const val FAR_PATH_STEPS_PER_TICK = 32
    private const val FAR_REPATH_SECONDS = 2.0
    private const val DIRECT_STALL = -2
    private const val OWNER_HALF_WIDTH = 0.3
    private const val OWNER_HEIGHT = 1.8
    private const val GRID_CELL = 2.0
    private const val SEPARATION_MIN = 0.01
    private const val SEPARATION_PUSH = 0.05
    private const val SEPARATION_LIMIT = 0.12
    private val SNAP_RINGS = doubleArrayOf(1.6, 2.6)
    private const val SNAP_TRIES = 8
    private const val SNAP_LIFTS = 2
    private const val SNAP_RETRY_SECONDS = 1.0
    private const val FLY_WAYPOINT_LIFT = 0.1
    private const val FLY_WAYPOINT_REACH = 0.5
    private const val FOOTING_AHEAD = 2.5
    private const val FOOTING_STEP = 0.5
    private const val FOOTING_RECHECK_TICKS = 4
    private const val LAND_SNAP = 0.12
    private const val MOOD_HALF_LIFE_TICKS = 600.0
    private const val MOOD_COMBAT_PER_TICK = 0.004f
    private const val MOOD_BORED_PER_TICK = 0.001f
    private const val MOOD_BORED_FLOOR = -0.5f
    private const val MOOD_BORED_SECONDS = 60.0
    private const val MOOD_SLEEP_FLOOR = 0.2f
    private const val MOOD_SLEEP_PER_TICK = 0.002f
    private val MOOD_DECAY = 0.5.pow(1.0 / MOOD_HALF_LIFE_TICKS).toFloat()

    private val move = PetCollision.Move()
    private val steer = PetSteer()
    private val waypoint = DoubleArray(3)
    private val spot = DoubleArray(3)
    private var direct = true
    private var closeRange = false
    private val nearPaths = PetPathQueue(PATH_STEPS_PER_TICK, PATH_STEPS_PER_PET)
    private val farPaths = PetPathQueue(FAR_PATH_STEPS_PER_TICK, FAR_PATH_STEPS_PER_PET)
    private val nearStepper = PetPathQueue.Stepper { index, turn -> stepPath(index, false, turn) }
    private val farStepper = PetPathQueue.Stepper { index, turn -> stepPath(index, true, turn) }
    private val bodies = PetGrid(GRID_CELL)
    private val owners = PetGrid(GRID_CELL)
    private val nearby = PetGrid.IntBag()
    private var crowd: List<PetManager.Pet> = emptyList()
    private var widest = 0.0

    fun halfWidth(asset: PetAsset): Double = BODY_WIDTH * asset.scale / 2.0

    fun height(asset: PetAsset): Double = BODY_HEIGHT * asset.scale

    fun gather(pets: List<PetManager.Pet>) {
        crowd = pets
        bodies.clear()
        owners.clear()
        widest = 0.0
        for (i in pets.indices) {
            val pet = pets[i]
            pet.crowdIndex = i
            if (!pet.present) continue
            owners.add(i, pet.ownerState.x, pet.ownerState.z)
            val asset = pet.asset ?: continue
            if (asset.archetype == PetArchetype.Shoulder) continue
            bodies.add(i, pet.body.x, pet.body.z)
            widest = max(widest, halfWidth(asset))
        }
    }

    fun advance(
        pet: PetManager.Pet,
        asset: PetAsset,
        owner: PetOwner,
        level: ClientLevel,
        now: Double,
        full: Boolean,
        pets: List<PetManager.Pet>,
    ) {
        val body = pet.body
        val current = floor(now / TICK_SECONDS + TICK_EPSILON).toLong()
        val previousLevel = body.level?.get()
        val stranded = previousLevel === level && stranded(body, asset, owner, level, now)
        if (!body.spawned || previousLevel !== level || now - body.lastSeen > ABSENT_RESPAWN_SECONDS || stranded) {
            val respawned = body.spawned
            spawn(body, asset, owner, level, previousLevel, now, current)
            if (respawned) WearerJumps.broke(pet)
        }
        body.lastSeen = now
        if (current - body.tickIndex > MAX_CATCH_UP_TICKS) body.tickIndex = current - 1
        while (body.tickIndex < current) {
            body.tickIndex++
            body.lastTick = body.tickIndex * TICK_SECONDS
            tick(pet, asset, owner, level, body.lastTick, full, pets)
        }
        val index = pet.crowdIndex
        if (index in crowd.indices && crowd[index] === pet) bodies.move(index, body.x, body.z)
    }

    fun stepPaths(now: Double) {
        val tick = floor(now / TICK_SECONDS + TICK_EPSILON).toLong()
        nearPaths.serve(tick, crowd.size, nearStepper)
        farPaths.serve(tick, crowd.size, farStepper)
    }

    private fun stepPath(index: Int, cheap: Boolean, turn: PetPathQueue.Turn): Boolean {
        val pet = crowd[index]
        if (!pet.present) return false
        val mind = pet.body.mind
        val path = mind.path
        if (path.state != PetPath.State.Searching || mind.pathCheap != cheap) return false
        turn.taken = path.step(turn.budget, turn.headroom)
        turn.work = path.work
        return true
    }

    internal fun strandedFor(body: PetBody, lost: Boolean, now: Double): Boolean {
        if (!lost) {
            body.unloadedSince = Double.NaN
            return false
        }
        if (body.unloadedSince.isNaN()) body.unloadedSince = now
        return now - body.unloadedSince >= ABSENT_RESPAWN_SECONDS
    }

    private fun stranded(body: PetBody, asset: PetAsset, owner: PetOwner, level: ClientLevel, now: Double): Boolean {
        if (!body.spawned || asset.archetype == PetArchetype.Shoulder) {
            body.unloadedSince = Double.NaN
            return false
        }
        val lost = !PetCollision.loaded(level, body.x, owner.y, body.z) && PetCollision.loaded(level, owner.x, owner.y, owner.z)
        return strandedFor(body, lost, now)
    }

    fun wrapDegrees(degrees: Float): Float {
        var wrapped = degrees % 360f
        if (wrapped >= 180f) wrapped -= 360f
        if (wrapped < -180f) wrapped += 360f
        return wrapped
    }

    fun gap(dx: Double, dy: Double, dz: Double, walking: Boolean): Double =
        if (walking && abs(dy) < CLIMB_GAP) sqrt(dx * dx + dz * dz) else sqrt(dx * dx + dy * dy + dz * dz)

    fun headingOf(dx: Double, dz: Double): Float? {
        if (dx * dx + dz * dz < 1.0e-8) return null
        return Math.toDegrees(atan2(-dx, dz)).toFloat()
    }

    private fun spawn(
        body: PetBody,
        asset: PetAsset,
        owner: PetOwner,
        level: ClientLevel,
        previousLevel: ClientLevel?,
        now: Double,
        current: Long,
    ) {
        body.level = WeakReference(level)
        body.spawned = true
        body.tickIndex = current
        body.lastTick = current * TICK_SECONDS
        body.ticks = 0
        body.unloadedSince = Double.NaN
        body.footingAt = Int.MIN_VALUE
        body.mood = 0f
        body.arrived = false
        body.mind.forget()
        body.mind.wasHurt = owner.flag(PlayerSignals.IS_HURT)
        if (previousLevel != null && previousLevel !== level) owner.combatUntil = Double.NEGATIVE_INFINITY
        val yaw = Math.toRadians(owner.bodyYaw.toDouble())
        val x = owner.x + sin(yaw) * SPAWN_BEHIND
        val z = owner.z - cos(yaw) * SPAWN_BEHIND
        val y = owner.y + if (asset.archetype == PetArchetype.Flying) FLY_HEIGHT else 0.0
        body.place(x, y, z, owner.bodyYaw)
        if (asset.archetype != PetArchetype.Shoulder &&
            PetCollision.overlaps(level, x, y, z, halfWidth(asset), height(asset))
        ) {
            if (findSpot(asset, owner, level)) {
                body.place(spot[0], spot[1], spot[2], owner.bodyYaw)
            } else {
                body.place(owner.x, owner.y, owner.z, owner.bodyYaw)
            }
        }
        if (asset.archetype == PetArchetype.Shoulder) body.place(owner.x, owner.y, owner.z, owner.bodyYaw)
        body.onGround = asset.archetype != PetArchetype.Flying
        body.distance = distance(body, owner).toFloat()
    }

    private fun tick(
        pet: PetManager.Pet,
        asset: PetAsset,
        owner: PetOwner,
        level: ClientLevel,
        now: Double,
        full: Boolean,
        pets: List<PetManager.Pet>,
    ) {
        val body = pet.body
        body.prevX = body.x
        body.prevY = body.y
        body.prevZ = body.z
        body.prevYaw = body.yaw
        body.prevPitch = body.pitch
        body.prevHeadYaw = body.headYaw
        body.prevHeadPitch = body.headPitch
        body.ticks++
        body.distance = distance(body, owner).toFloat()

        PetBrain.think(pet, asset, owner, level, now, PetCollision.time(level), full, pets, steer)
        val mind = body.mind
        if (mind.snapNow) {
            mind.snapNow = false
            if (snap(body, asset, owner, level, now)) WearerJumps.broke(pet)
        } else {
            when (asset.archetype) {
                PetArchetype.Shoulder -> {
                    body.x = owner.x
                    body.y = owner.y
                    body.z = owner.z
                    body.yaw = owner.bodyYaw
                    body.onGround = true
                    body.inWater = PetCollision.inWater(level, owner.x, owner.y + height(asset), owner.z)
                }
                PetArchetype.Walking -> walk(pet, asset, owner, level, now, full)
                PetArchetype.Flying -> fly(pet, asset, owner, level, now, full)
            }
        }
        head(body)
        if (asset.archetype != PetArchetype.Shoulder) turnBody(body)
        body.yaw = wrapDegrees(body.yaw)
        body.headYaw = wrapDegrees(body.headYaw)
        body.stableHeadYaw = wrapDegrees(body.stableHeadYaw)

        val dx = body.x - body.prevX
        val dy = body.y - body.prevY
        val dz = body.z - body.prevZ
        val stride = if (asset.archetype == PetArchetype.Flying) sqrt(dx * dx + dy * dy + dz * dz) else sqrt(dx * dx + dz * dz)
        body.speed = (stride / TICK_SECONDS).toFloat()
        body.limb += (min(stride.toFloat() * LIMB_SCALE, 1f) - body.limb) * LIMB_EASE
        body.gait = body.limb > if (body.gait) GAIT_OFF else GAIT_ON
        body.distance = distance(body, owner).toFloat()
        if (asset.archetype == PetArchetype.Flying) {
            val horizontal = sqrt(dx * dx + dz * dz)
            val wanted = if (body.onGround) {
                0f
            } else {
                Math.toDegrees(atan2(-dy, horizontal + PITCH_FLOOR)).coerceIn(-MAX_PITCH_DEGREES, MAX_PITCH_DEGREES).toFloat()
            }
            body.pitch += (wanted - body.pitch) * PITCH_EASE
        } else {
            body.pitch = 0f
        }
        body.mode = PetBrain.mode(pet, asset, owner)
        body.action = PetBrain.action(pet)
        mood(body, owner, owner.flag(PlayerSignals.IS_SLEEPING), now)
    }

    private fun walk(
        pet: PetManager.Pet,
        asset: PetAsset,
        owner: PetOwner,
        level: ClientLevel,
        now: Double,
        full: Boolean,
    ) {
        val body = pet.body
        val halfWidth = halfWidth(asset)
        val height = height(asset)
        var ax = 0.0
        var az = 0.0
        var dirX = 0.0
        var dirZ = 0.0
        var jump = false
        var wanting = false
        if (steer.moving) {
            navigate(body, asset, level, now, full, false)
            val dx = waypoint[0] - body.x
            val dz = waypoint[2] - body.z
            val length = sqrt(dx * dx + dz * dz)
            val rise = steer.y - body.y
            val remaining = when {
                steer.climb && abs(rise) > CLIMB_SLACK -> {
                    val flat = horizontalTo(body, steer.x, steer.z)
                    sqrt(flat * flat + rise * rise)
                }
                direct -> horizontalTo(body, steer.x, steer.z)
                else -> gap(steer.x - body.x, rise, steer.z - body.z, true)
            }
            wanting = remaining > steer.stopRadius && length > 1.0e-4
            if (wanting && (!direct || footingClear(body, asset, level))) {
                body.yaw = turn(body.yaw, headingOf(dx, dz) ?: body.yaw, MAX_TURN)
                val speed = min(steer.speed, max(MIN_SPEED, (remaining - steer.stopRadius) * ARRIVE_SHARE))
                val accel = if (body.onGround || body.inWater) speed * (1.0 - GROUND_FRICTION) else AIR_ACCEL
                val yaw = Math.toRadians(body.yaw.toDouble())
                dirX = -sin(yaw)
                dirZ = cos(yaw)
                ax = dirX * accel
                az = dirZ * accel
                jump = body.onGround && waypoint[1] > body.y + STEP_HEIGHT && length < JUMP_REACH
            }
        }
        if (body.inWater) {
            ax *= WATER_SPEED
            az *= WATER_SPEED
        }
        separate(pet, asset)
        body.vx += ax + separation[0] * (1.0 - GROUND_FRICTION)
        body.vz += az + separation[1] * (1.0 - GROUND_FRICTION)

        val grounded = body.onGround
        body.inWater = PetCollision.inWater(level, body.x, body.y + height * 0.5, body.z)
        if (jump && !body.inWater) {
            launch(body, dirX, dirZ)
        } else if (steer.hop && grounded && !body.inWater) {
            body.vy = JUMP_VELOCITY
        }
        if (body.inWater) body.vy = min(body.vy * WATER_DRAG + WATER_RISE, WATER_RISE * 2)
        if (!PetCollision.loaded(level, body.x, body.y, body.z)) body.vy = 0.0
        val pushX = body.vx
        val pushZ = body.vz
        PetCollision.move(level, body.x, body.y, body.z, halfWidth, height, body.vx, body.vy, body.vz, STEP_HEIGHT, move)
        body.x += move.dx
        body.y += move.dy
        body.z += move.dz
        body.onGround = move.onGround
        if (move.dy != body.vy && body.vy < JUMP_VELOCITY) body.vy = 0.0
        val friction = when {
            body.inWater -> WATER_DRAG
            grounded -> GROUND_FRICTION
            else -> AIR_FRICTION
        }
        body.vx = move.dx * friction
        body.vz = move.dz * friction
        if (ax == 0.0 && az == 0.0 && body.vx * body.vx + body.vz * body.vz < REST_SPEED * REST_SPEED) {
            body.vx = 0.0
            body.vz = 0.0
        }
        if (!body.inWater) body.vy = max((body.vy - GRAVITY) * DRAG, TERMINAL)
        watch(body, ax != 0.0 || az != 0.0, wanting, false)
        if (move.blocked && body.onGround && steer.moving && (pushX != 0.0 || pushZ != 0.0) && !body.inWater) {
            val probeX = body.x + sign(pushX) * (halfWidth + 0.3)
            val probeZ = body.z + sign(pushZ) * (halfWidth + 0.3)
            val ledge = PetCollision.surface(level, probeX, probeZ, halfWidth, height, body.y + PetPath.JUMP, body.y + STEP_HEIGHT)
            if (!ledge.isNaN()) launch(body, pushX, pushZ)
        }
    }

    private fun launch(body: PetBody, dirX: Double, dirZ: Double) {
        body.vy = JUMP_VELOCITY
        val length = sqrt(dirX * dirX + dirZ * dirZ)
        if (length < 1.0e-6) return
        val carry = max(sqrt(body.vx * body.vx + body.vz * body.vz), JUMP_CARRY)
        body.vx = dirX / length * carry
        body.vz = dirZ / length * carry
    }

    private fun fly(
        pet: PetManager.Pet,
        asset: PetAsset,
        owner: PetOwner,
        level: ClientLevel,
        now: Double,
        full: Boolean,
    ) {
        val body = pet.body
        val halfWidth = halfWidth(asset)
        val height = height(asset)
        var wantX = 0.0
        var wantY = 0.0
        var wantZ = 0.0
        var trying = false
        if (steer.moving) {
            navigate(body, asset, level, now, full, true)
            val dx = waypoint[0] - body.x
            val dy = waypoint[1] - body.y
            val dz = waypoint[2] - body.z
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            val tx = steer.x - body.x
            val ty = steer.y - body.y
            val tz = steer.z - body.z
            val remaining = sqrt(tx * tx + ty * ty + tz * tz)
            if (remaining > steer.stopRadius && length > 1.0e-4) {
                val speed = min(steer.speed, max(MIN_SPEED * 0.5, remaining * ARRIVE_GAIN))
                wantX = dx / length * speed
                wantY = dy / length * speed
                wantZ = dz / length * speed
                trying = true
            }
        }
        separate(pet, asset)
        wantX += separation[0]
        wantZ += separation[1]
        accelerate(body, wantX, wantZ, FLY_ACCEL)
        body.vy += (wantY - body.vy).coerceIn(-FLY_ACCEL, FLY_ACCEL)
        if (body.onGround && wantY <= 0.0 && steer.land) body.vy = 0.0
        if (!safeFlight(body, level, halfWidth, height)) {
            body.vx = 0.0
            body.vy = 0.0
            body.vz = 0.0
            if (steer.moving) {
                if (!direct) {
                    body.mind.path.clear()
                } else if (closeRange) {
                    body.mind.detour = true
                }
            }
        }
        PetCollision.move(level, body.x, body.y, body.z, halfWidth, height, body.vx, body.vy, body.vz, 0.0, move)
        body.x += move.dx
        body.y += move.dy
        body.z += move.dz
        if (abs(move.dx - body.vx) > 1.0e-6) body.vx = move.dx
        if (abs(move.dy - body.vy) > 1.0e-6) body.vy = move.dy
        if (abs(move.dz - body.vz) > 1.0e-6) body.vz = move.dz
        body.onGround = steer.land && (move.onGround || abs(body.y - steer.y) < LAND_SNAP && horizontalTo(body, steer.x, steer.z) < 0.3)
        body.inWater = PetCollision.inWater(level, body.x, body.y, body.z)
        watch(body, trying, trying, true)
        val heading = headingOf(body.vx, body.vz)
        if (heading != null) body.moveYaw = heading
        if (heading != null && sqrt(body.vx * body.vx + body.vz * body.vz) > MIN_SPEED * 0.5) {
            body.yaw = turn(body.yaw, heading, FLY_TURN, TURN_EASE)
        }
    }

    private fun safeFlight(body: PetBody, level: ClientLevel, halfWidth: Double, height: Double): Boolean {
        val vx = body.vx
        val vy = body.vy
        val vz = body.vz
        if (vx == 0.0 && vy == 0.0 && vz == 0.0) return true
        if (PetCollision.sweep(level, body.x, body.y, body.z, vx, vy, vz, halfWidth, height) != PetCollision.DANGER) return true
        if (PetCollision.hazard(level, body.x, body.y, body.z, halfWidth, height) == PetCollision.DANGER) return true
        if (vy != 0.0 && (vx != 0.0 || vz != 0.0) &&
            PetCollision.sweep(level, body.x, body.y, body.z, vx, 0.0, vz, halfWidth, height) != PetCollision.DANGER
        ) {
            body.vy = 0.0
            return true
        }
        if (vy != 0.0 && (vx != 0.0 || vz != 0.0) &&
            PetCollision.sweep(level, body.x, body.y, body.z, 0.0, vy, 0.0, halfWidth, height) != PetCollision.DANGER
        ) {
            body.vx = 0.0
            body.vz = 0.0
            return true
        }
        return false
    }

    private fun navigate(body: PetBody, asset: PetAsset, level: ClientLevel, now: Double, full: Boolean, flying: Boolean) {
        waypoint[0] = steer.x
        waypoint[1] = steer.y
        waypoint[2] = steer.z
        direct = true
        val mind = body.mind
        val path = mind.path
        val dx = steer.x - body.x
        val dy = steer.y - body.y
        val dz = steer.z - body.z
        val climbGap = if (steer.climb && !flying) CLIMB_SLACK else CLIMB_GAP
        val approaching = body.directive.active && !body.directive.started
        val close = dx * dx + dz * dz < DIRECT_BLOCKS * DIRECT_BLOCKS && abs(dy) < climbGap
        closeRange = close && steer.path
        if (!steer.path || close && !mind.detour) {
            mind.detour = false
            path.clear()
            return
        }
        if (!close) mind.detour = false
        val cheap = !full && !approaching
        mind.pathCheap = cheap
        val goalShift = (path.goalX - steer.x) * (path.goalX - steer.x) +
            (path.goalY - steer.y) * (path.goalY - steer.y) +
            (path.goalZ - steer.z) * (path.goalZ - steer.z)
        if (mind.detour && (
                path.state == PetPath.State.Failed ||
                    path.state == PetPath.State.Found && path.index >= path.count ||
                    path.state != PetPath.State.Idle && goalShift > REPATH_BLOCKS * REPATH_BLOCKS
                )
        ) {
            mind.detour = false
            path.clear()
            return
        }
        val stale = path.state == PetPath.State.Idle || path.state == PetPath.State.Failed || goalShift > REPATH_BLOCKS * REPATH_BLOCKS ||
            path.state == PetPath.State.Found && path.index >= path.count
        if (stale && path.state != PetPath.State.Searching && now >= mind.nextPathAt) {
            mind.nextPathAt = now + if (cheap) FAR_REPATH_SECONDS else REPATH_SECONDS
            mind.stall.reset()
            val swimmer = body.inWater
            path.start(level, body.x, body.y, body.z, steer.x, steer.y, steer.z, flying, swimmer, halfWidth(asset), height(asset))
        }
        if (!path.active) return
        while (path.index < path.count) {
            val px = path.x(path.index) - body.x
            val pz = path.z(path.index) - body.z
            val nodeY = path.y(path.index) + if (flying) FLY_WAYPOINT_LIFT else 0.0
            val near = px * px + pz * pz < WAYPOINT_REACH * WAYPOINT_REACH &&
                abs(nodeY - body.y) < if (flying) FLY_WAYPOINT_REACH else 1.1
            if (!near) break
            path.index++
        }
        if (path.index >= path.count) return
        waypoint[0] = path.x(path.index)
        waypoint[1] = path.y(path.index) + if (flying) FLY_WAYPOINT_LIFT else 0.0
        waypoint[2] = path.z(path.index)
        direct = false
    }

    private fun watch(body: PetBody, trying: Boolean, wanting: Boolean, flying: Boolean) {
        val mind = body.mind
        val path = mind.path
        if (direct && closeRange && wanting) {
            val dx = steer.x - body.x
            val dy = if (flying) steer.y - body.y else 0.0
            val dz = steer.z - body.z
            if (mind.stall.stuck(DIRECT_STALL, sqrt(dx * dx + dy * dy + dz * dz))) mind.detour = true
            return
        }
        if (direct || !trying || !path.active) {
            mind.stall.reset()
            return
        }
        val dx = waypoint[0] - body.x
        val dy = if (flying) waypoint[1] - body.y else 0.0
        val dz = waypoint[2] - body.z
        if (mind.stall.stuck(path.index, sqrt(dx * dx + dy * dy + dz * dz))) path.clear()
    }

    private fun footingClear(body: PetBody, asset: PetAsset, level: ClientLevel): Boolean {
        val shiftX = waypoint[0] - body.footingX
        val shiftZ = waypoint[2] - body.footingZ
        if (!PetTimers.due(body.ticks, body.footingAt, FOOTING_RECHECK_TICKS) && shiftX * shiftX + shiftZ * shiftZ < 1.0) {
            return body.footingClear
        }
        body.footingAt = body.ticks
        body.footingX = waypoint[0]
        body.footingZ = waypoint[2]
        body.footingClear = footing(body, asset, level, waypoint[0], waypoint[2])
        return body.footingClear
    }

    private fun footing(body: PetBody, asset: PetAsset, level: ClientLevel, toX: Double, toZ: Double): Boolean {
        val dx = toX - body.x
        val dz = toZ - body.z
        val length = sqrt(dx * dx + dz * dz)
        if (length < 1.0e-4) return true
        val reach = min(length, FOOTING_AHEAD)
        val steps = ceil(reach / FOOTING_STEP).toInt().coerceAtLeast(1)
        val halfWidth = halfWidth(asset)
        val height = height(asset)
        val swimmer = body.inWater
        var y = body.y
        for (i in 1..steps) {
            val t = reach * i / steps / length
            val x = body.x + dx * t
            val z = body.z + dz * t
            val ground = PetCollision.surface(level, x, z, halfWidth, height, y + PetPath.JUMP, y - PetPath.MAX_DROP)
            if (ground.isNaN()) return false
            val hazard = PetCollision.hazard(level, x, ground, z, halfWidth, height)
            if (hazard == PetCollision.DANGER || hazard == PetCollision.WATER && !swimmer) return false
            y = ground
        }
        return true
    }

    private val separation = DoubleArray(2)

    private fun separate(pet: PetManager.Pet, asset: PetAsset) {
        separation[0] = 0.0
        separation[1] = 0.0
        val body = pet.body
        val mine = halfWidth(asset)
        val top = body.y + height(asset)
        bodies.near(body.x, body.z, mine + widest, nearby)
        for (n in 0 until nearby.size) {
            val other = crowd[nearby[n]]
            if (other === pet || !other.present) continue
            val otherAsset = other.asset ?: continue
            if (otherAsset.archetype == PetArchetype.Shoulder) continue
            val o = other.body
            if (o.y > top || o.y + height(otherAsset) < body.y) continue
            push(body, o.x, o.z, mine + halfWidth(otherAsset), pet.body.mind.seed xor other.body.mind.seed)
        }
        owners.near(body.x, body.z, mine + OWNER_HALF_WIDTH, nearby)
        for (n in 0 until nearby.size) {
            val other = crowd[nearby[n]]
            if (!other.present) continue
            val o = other.ownerState
            if (o.y > top || o.y + OWNER_HEIGHT < body.y) continue
            push(body, o.x, o.z, mine + OWNER_HALF_WIDTH, pet.body.mind.seed)
        }
        val length = sqrt(separation[0] * separation[0] + separation[1] * separation[1])
        if (length > SEPARATION_LIMIT) {
            separation[0] *= SEPARATION_LIMIT / length
            separation[1] *= SEPARATION_LIMIT / length
        }
    }

    private fun push(body: PetBody, x: Double, z: Double, reach: Double, seed: Long) {
        var dx = body.x - x
        var dz = body.z - z
        if (abs(dx) >= reach || abs(dz) >= reach) return
        val largest = max(abs(dx), abs(dz))
        if (largest < SEPARATION_MIN) {
            val angle = PetMind.unit(seed, 7) * Math.PI * 2.0
            dx = cos(angle)
            dz = sin(angle)
        } else {
            val root = sqrt(largest)
            val scale = min(1.0, 1.0 / root) / root
            dx *= scale
            dz *= scale
        }
        separation[0] += dx * SEPARATION_PUSH
        separation[1] += dz * SEPARATION_PUSH
    }

    private fun accelerate(body: PetBody, wantX: Double, wantZ: Double, accel: Double) {
        var dvx = wantX - body.vx
        var dvz = wantZ - body.vz
        val change = sqrt(dvx * dvx + dvz * dvz)
        if (change > accel) {
            dvx *= accel / change
            dvz *= accel / change
        }
        body.vx += dvx
        body.vz += dvz
        if (wantX == 0.0 && wantZ == 0.0 && body.vx * body.vx + body.vz * body.vz < REST_SPEED * REST_SPEED) {
            body.vx = 0.0
            body.vz = 0.0
        }
    }

    private fun turnBody(body: PetBody) {
        val dx = body.x - body.prevX
        val dz = body.z - body.prevZ
        when {
            dx * dx + dz * dz > STILL_SQR -> {
                body.headYaw = rotateIfNecessary(body.headYaw, body.yaw, HEAD_YAW_LIMIT)
                body.stableHeadYaw = body.headYaw
                body.headStable = 0
            }
            !steer.faceYaw.isNaN() -> {
                body.yaw = turn(body.yaw, steer.faceYaw, FACE_TURN)
                body.stableHeadYaw = body.headYaw
                body.headStable = 0
            }
            abs(wrapDegrees(body.headYaw - body.stableHeadYaw)) > HEAD_STABLE_ANGLE -> {
                body.headStable = 0
                body.stableHeadYaw = body.headYaw
                body.yaw = rotateIfNecessary(body.yaw, body.headYaw, BODY_FOLLOWS_HEAD)
            }
            else -> {
                body.headStable++
                if (body.headStable > HEAD_STABLE_TICKS) {
                    val forward = ((body.headStable - HEAD_STABLE_TICKS) / FACE_FORWARD_TICKS).coerceIn(0f, 1f)
                    body.yaw = rotateIfNecessary(body.yaw, body.headYaw, BODY_FOLLOWS_HEAD * (1f - forward))
                }
            }
        }
    }

    private fun head(body: PetBody) {
        if (steer.looking) {
            val dx = steer.lookX - body.x
            val dz = steer.lookZ - body.z
            val yaw = headingOf(dx, dz)
            if (yaw != null) body.headYaw = turn(body.headYaw, yaw, HEAD_TURN)
            val eye = body.y + BODY_HEIGHT * 0.7
            val pitch = (-Math.toDegrees(atan2(steer.lookY - eye, sqrt(dx * dx + dz * dz)))).toFloat()
            body.headPitch = approach(body.headPitch, pitch.coerceIn(-HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT), HEAD_PITCH_TURN)
        } else {
            val yaw = if (steer.glanceYaw.isNaN()) body.yaw else steer.glanceYaw
            body.headYaw = turn(body.headYaw, yaw, HEAD_TURN)
            body.headPitch = approach(body.headPitch, 0f, HEAD_PITCH_TURN)
        }
        if (steer.moving) body.headYaw = rotateIfNecessary(body.headYaw, body.yaw, HEAD_YAW_LIMIT)
    }

    private fun snap(body: PetBody, asset: PetAsset, owner: PetOwner, level: ClientLevel, now: Double): Boolean {
        val halfWidth = halfWidth(asset)
        val height = height(asset)
        val flying = asset.archetype == PetArchetype.Flying
        val fromX = body.x
        val fromY = body.y
        val fromZ = body.z
        if (!findSpot(asset, owner, level)) {
            body.mind.snapRetryAt = now + SNAP_RETRY_SECONDS
            return false
        }
        body.mind.ownerJumped = false
        body.place(spot[0], spot[1], spot[2], owner.bodyYaw)
        body.onGround = !flying
        body.mind.path.clear()
        body.mind.goal = PetGoal.Rest
        body.mind.leisureUntil = now
        body.appearAt = now
        if (body.visible) {
            val seed = body.mind.seed xor body.ticks.toLong()
            PetSenses.poof(level, fromX, fromY + height * 0.5, fromZ, seed)
            PetSenses.poof(level, body.x, body.y + height * 0.5, body.z, seed + 1)
        }
        return true
    }

    private fun findSpot(asset: PetAsset, owner: PetOwner, level: ClientLevel): Boolean {
        val halfWidth = halfWidth(asset)
        val height = height(asset)
        val flying = asset.archetype == PetArchetype.Flying
        val yaw = Math.toRadians(owner.bodyYaw.toDouble())
        for (ring in SNAP_RINGS) {
            for (i in 0 until SNAP_TRIES) {
                val turns = (i + 1) / 2
                val side = if (i % 2 == 1) 1.0 else -1.0
                val angle = yaw + side * turns * Math.PI * 2.0 / SNAP_TRIES
                val x = owner.x + sin(angle) * ring
                val z = owner.z - cos(angle) * ring
                if (flying) {
                    for (lift in 0..SNAP_LIFTS) {
                        val y = owner.y + FLY_HEIGHT + lift
                        if (PetCollision.overlaps(level, x, y, z, halfWidth, height)) continue
                        if (PetCollision.hazard(level, x, y, z, halfWidth, height) == PetCollision.DANGER) continue
                        spot[0] = x
                        spot[1] = y
                        spot[2] = z
                        return true
                    }
                } else {
                    val ground = PetCollision.surface(level, x, z, halfWidth, height, owner.y + SNAP_LIFTS, owner.y - PetPath.MAX_DROP)
                    if (ground.isNaN() || PetCollision.hazard(level, x, ground, z, halfWidth, height) != PetCollision.SAFE) continue
                    spot[0] = x
                    spot[1] = ground
                    spot[2] = z
                    return true
                }
            }
        }
        return false
    }

    private fun mood(body: PetBody, owner: PetOwner, sleeping: Boolean, now: Double) {
        body.mood *= MOOD_DECAY
        val combat = owner.inCombat(now)
        if (combat) body.mood = max(-1f, body.mood - MOOD_COMBAT_PER_TICK)
        if (sleeping && body.mood < MOOD_SLEEP_FLOOR) body.mood = min(MOOD_SLEEP_FLOOR, body.mood + MOOD_SLEEP_PER_TICK)
        if (!sleeping && owner.idleFor(now) > MOOD_BORED_SECONDS && body.mood > MOOD_BORED_FLOOR) {
            body.mood = max(MOOD_BORED_FLOOR, body.mood - MOOD_BORED_PER_TICK)
        }
    }

    private fun horizontalTo(body: PetBody, x: Double, z: Double): Double {
        val dx = x - body.x
        val dz = z - body.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun distance(body: PetBody, owner: PetOwner): Double {
        val dx = body.x - owner.x
        val dy = body.y - owner.y
        val dz = body.z - owner.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun sign(value: Double): Double = when {
        value > 1.0e-4 -> 1.0
        value < -1.0e-4 -> -1.0
        else -> 0.0
    }

    private fun turn(from: Float, to: Float, rate: Float, ease: Float = 1f): Float {
        val difference = wrapDegrees(to - from)
        return from + (difference * ease).coerceIn(-rate, rate)
    }

    private fun approach(from: Float, to: Float, rate: Float): Float = from + (to - from).coerceIn(-rate, rate)

    private fun rotateIfNecessary(current: Float, target: Float, limit: Float): Float =
        target - wrapDegrees(target - current).coerceIn(-limit, limit)
}
