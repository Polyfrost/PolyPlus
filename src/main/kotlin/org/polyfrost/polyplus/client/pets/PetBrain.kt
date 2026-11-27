package org.polyfrost.polyplus.client.pets

import net.minecraft.client.multiplayer.ClientLevel
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class PetGoal(val priority: Int, val leisure: Boolean) {
    CatchUp(1, false),
    React(2, false),
    Play(3, false),
    Follow(4, false),
    Look(5, true),
    Wander(6, true),
    Idle(7, true),
    Rest(8, true),
}

internal class PetSteer {
    var moving = false
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var speed = 0.0
    var stopRadius = 0.0
    var path = false
    var land = false
    var faceYaw = Float.NaN
    var looking = false
    var lookX = 0.0
    var lookY = 0.0
    var lookZ = 0.0
    var glanceYaw = Float.NaN
    var climb = false
    var hop = false

    fun reset() {
        moving = false
        climb = false
        hop = false
        speed = 0.0
        stopRadius = 0.0
        path = false
        land = false
        faceYaw = Float.NaN
        looking = false
        glanceYaw = Float.NaN
    }

    fun go(x: Double, y: Double, z: Double, speed: Double, stopRadius: Double, path: Boolean) {
        moving = true
        this.x = x
        this.y = y
        this.z = z
        this.speed = speed
        this.stopRadius = stopRadius
        this.path = path
    }

    fun look(x: Double, y: Double, z: Double) {
        looking = true
        lookX = x
        lookY = y
        lookZ = z
    }
}

internal class PetMind(val seed: Long) {
    var goal = PetGoal.Rest
    var goalSince = 0.0
    private val thinkOffset = (seed and 3L).toInt()
    private val scanOffset = (seed ushr 8 and 7L).toInt()
    var nextThink = thinkOffset
    var leisureUntil = 0.0
    var lookReadyAt = 0.0
    var wanderReadyAt = 0.0
    var hasTarget = false
    var targetX = 0.0
    var targetY = 0.0
    var targetZ = 0.0
    var perch = false
    var pauseUntil = Double.NaN
    var action = 0
    var sleeping = false
    var ownerSit = false
    var calmSince = Double.NaN
    var gazeUntil = 0.0
    var flinchUntil = 0.0
    var closerUntil = 0.0
    var wasHurt = false
    var sneakingSince = Double.NaN
    var glideWaiting = false
    var ownerJumped = false
    var ownerPrevX = Double.NaN
    var ownerPrevY = 0.0
    var ownerPrevZ = 0.0
    var ownerPrevTick = 0L
    var ownerSpeed = 0.0
    var ownerStoppedAt = Double.NaN
    var closing = false
    var glanceYaw = Float.NaN
    var glanceUntil = 0.0
    var glanceNext = 0.0
    var nextScan = scanOffset
    var focusUntil = 0.0
    var sightCount = 0
    var snapNow = false
    var alarmed = false
    var threat = false
    var snapRetryAt = 0.0
    var nextPathAt = 0.0
    var pathCheap = false
    var detour = false
    val sights = Array(MAX_SIGHTS) { PetSight() }
    val focus = PetSight()
    val path = PetPath()
    val stall = PetStall()

    val flyUp = 1.85 + unit(seed, 6) * 0.5

    fun random(salt: Long, time: Long): Double = unit(seed xor (salt * SALT_MIX), floor(time / DECISION_TICKS.toDouble()).toLong())

    fun roll(salt: Long, time: Long): Double = unit(seed xor (salt * SALT_MIX), time)

    fun forget() {
        goal = PetGoal.Rest
        nextThink = thinkOffset
        nextScan = scanOffset
        leisureUntil = 0.0
        lookReadyAt = 0.0
        wanderReadyAt = 0.0
        focusUntil = 0.0
        nextPathAt = 0.0
        snapRetryAt = 0.0
        hasTarget = false
        perch = false
        pauseUntil = Double.NaN
        action = 0
        sleeping = false
        ownerSit = false
        calmSince = Double.NaN
        gazeUntil = 0.0
        glideWaiting = false
        ownerJumped = false
        closing = false
        glanceYaw = Float.NaN
        glanceNext = 0.0
        sightCount = 0
        alarmed = false
        threat = false
        focus.clear()
        for (sight in sights) sight.clear()
        path.clear()
        stall.reset()
        detour = false
        ownerPrevX = Double.NaN
        ownerStoppedAt = Double.NaN
    }

    companion object {
        const val MAX_SIGHTS = 8
        private const val DECISION_TICKS = 20
        private const val SALT_MIX = -0x61c8864680b583ebL

        fun mix(value: Long): Long {
            var z = value + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        fun unit(seed: Long, salt: Long): Double = (mix(seed xor mix(salt)) ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}

internal object PetBrain {
    const val THINK_TICKS = 5
    const val WALK = 0.194
    const val STROLL = 0.124
    const val RUN = 0.3
    const val SPRINT = 0.42
    const val FLY = 0.2

    private const val SCAN_TICKS = 10
    private const val LOOK_RADIUS = 8.0
    private const val CLOSE_PLAYER = 4.0
    private const val HOSTILE_ALERT = 5.0
    private const val BACK_AWAY = 3.5
    private const val BACK_AWAY_STEP = 1.6
    private const val CATCH_UP_LEASHES = 4.0
    private const val SPRINT_LEASHES = 6.0
    private const val OWNER_JUMP_BLOCKS = 16.0
    private const val FOLLOW_START_LEASHES = 2.0
    private const val FOLLOW_STOP = 2.0
    private const val COMBAT_START_LEASHES = 1.0
    private const val COMBAT_STOP = 1.2
    private const val WANDER_MARGIN = 1.0
    private const val WANDER_AFTER = 4.0
    private const val IDLE_ACTIONS_AFTER = 20.0
    private const val SLEEP_AFTER = 60.0
    private const val OWNER_STOPPED_INTEREST = 3.0
    private const val SIT_SECONDS = 3.0
    private const val FLINCH_SECONDS = 0.6
    private const val CLOSER_SECONDS = 5.0
    private const val CLOSER_FACTOR = 0.5
    private const val SLEEP_SIDE = 0.9
    private const val SLIPSTREAM_BEHIND = 1.6
    private const val SLIPSTREAM_HEIGHT = 0.3
    private const val CIRCLE_RADIUS = 1.1
    private const val CIRCLE_HEIGHT = 1.2
    private const val CIRCLE_SPEED = 1.6
    private const val FLY_BOB_HEIGHT = 0.15
    private const val FLY_BOB_PERIOD = 12.0
    private const val CHASE_SWAP_SECONDS = 4.0
    private const val FOLLOW_CIRCLE_SPEED = 1.8
    private const val MARK_ARRIVED = 0.35
    private const val HOP_SECONDS = 0.8
    private const val HOP_LIFT = 0.4

    fun think(
        pet: PetManager.Pet,
        asset: PetAsset,
        owner: PetOwner,
        level: ClientLevel,
        now: Double,
        time: Long,
        full: Boolean,
        pets: List<PetManager.Pet>,
        steer: PetSteer,
    ) {
        val body = pet.body
        val mind = body.mind
        observe(body, owner, now, asset.leashRadius.toDouble(), asset.archetype == PetArchetype.Shoulder)
        if (full) {
            attend(pet, mind, owner, level, now, time, pets)
        } else {
            mind.focus.clear()
            mind.glideWaiting = false
        }
        val keep = canContinue(pet, asset, owner, now, full)
        if (body.ticks >= mind.nextThink || !keep) {
            mind.nextThink = body.ticks + THINK_TICKS
            choose(pet, asset, owner, level, now, time, full, keep)
        }
        if (!mind.goal.leisure || mind.calmSince.isNaN()) mind.calmSince = now
        steer.reset()
        val flying = asset.archetype == PetArchetype.Flying
        when (mind.goal) {
            PetGoal.CatchUp -> catchUp(body, asset.leashRadius.toDouble(), flying, owner, now, steer)
            PetGoal.React -> react(body, asset, owner, now, steer)
            PetGoal.Play -> play(pet, asset, now, steer)
            PetGoal.Follow -> follow(body, flying, owner, now, steer)
            PetGoal.Look -> look(body, asset, owner, steer)
            PetGoal.Wander -> wander(body, asset, now, time, steer)
            PetGoal.Idle -> idle(body, owner, now, steer)
            PetGoal.Rest -> {}
        }
        if (!steer.looking && steer.glanceYaw.isNaN()) glance(body, mind, now, time, steer)
    }

    fun mode(pet: PetManager.Pet, asset: PetAsset, owner: PetOwner): Float = mode(pet.body, asset.archetype, owner)

    internal fun mode(body: PetBody, archetype: PetArchetype, owner: PetOwner): Float {
        val mind = body.mind
        val moving = body.gait
        val napping = body.directive.interaction == PetInteraction.Nap && body.directive.started && mind.goal == PetGoal.Play
        return when {
            owner.flag(PlayerSignals.IS_SLEEPING) || napping || mind.sleeping -> PetVariables.SLEEPING
            mind.goal == PetGoal.Play && body.directive.active -> PetVariables.PLAYING
            owner.flag(PlayerSignals.IS_RIDING) && (archetype == PetArchetype.Shoulder || !moving) -> PetVariables.RIDING
            mind.ownerSit && mind.goal == PetGoal.Idle && archetype != PetArchetype.Flying -> PetVariables.SITTING
            archetype == PetArchetype.Shoulder -> when {
                mind.goal == PetGoal.Play -> PetVariables.PLAYING
                mind.goal == PetGoal.Idle && mind.action != 0 -> PetVariables.IDLE_ACTION
                owner.flag(PlayerSignals.IS_MOVING) -> PetVariables.FOLLOWING
                else -> PetVariables.IDLE
            }
            mind.goal == PetGoal.CatchUp && moving -> PetVariables.CATCHING_UP
            mind.goal == PetGoal.Play -> PetVariables.PLAYING
            mind.goal == PetGoal.Wander -> PetVariables.WANDERING
            mind.goal == PetGoal.Idle && mind.action != 0 -> PetVariables.IDLE_ACTION
            moving -> PetVariables.FOLLOWING
            else -> PetVariables.IDLE
        }
    }

    fun action(pet: PetManager.Pet): Float {
        val mind = pet.body.mind
        return when (mind.goal) {
            PetGoal.Idle, PetGoal.Wander -> mind.action.toFloat()
            else -> 0f
        }
    }

    fun ownerFree(pet: PetManager.Pet): Boolean {
        val asset = pet.asset ?: return false
        return ownerFree(asset.archetype, pet.ownerState, pet.body.mind, pet.body.lastTick)
    }

    internal fun ownerFree(archetype: PetArchetype, owner: PetOwner, mind: PetMind, now: Double): Boolean {
        if (now < mind.flinchUntil) return false
        if (owner.flag(PlayerSignals.IS_EMOTING) || owner.flag(PlayerSignals.IS_SLEEPING) || owner.flag(PlayerSignals.IS_GLIDING)) return false
        return archetype == PetArchetype.Shoulder || !owner.inCombat(now)
    }

    fun playRoom(pet: PetManager.Pet, asset: PetAsset, x: Double, y: Double, z: Double): Double {
        if (asset.archetype == PetArchetype.Shoulder) return Double.POSITIVE_INFINITY
        val owner = pet.ownerState
        val body = pet.body
        val dx = x - owner.x
        val dy = y - owner.y
        val dz = z - owner.z
        return followStart(asset, body.mind, body.lastTick) - sqrt(dx * dx + dy * dy + dz * dz)
    }

    internal fun observe(body: PetBody, owner: PetOwner, now: Double, leash: Double, shoulder: Boolean) {
        val mind = body.mind
        val hurt = owner.flag(PlayerSignals.IS_HURT)
        if (hurt && !mind.wasHurt) {
            mind.flinchUntil = now + FLINCH_SECONDS
            mind.closerUntil = now + CLOSER_SECONDS
            body.cheer(MOOD_HURT)
        }
        mind.wasHurt = hurt
        if (owner.flag(PlayerSignals.IS_SNEAKING) && !owner.flag(PlayerSignals.IS_MOVING)) {
            if (mind.sneakingSince.isNaN()) mind.sneakingSince = now
        } else {
            mind.sneakingSince = Double.NaN
        }
        val farBehind = !shoulder && body.distance > leash * CATCH_UP_LEASHES
        if (!mind.ownerPrevX.isNaN()) {
            val dx = owner.x - mind.ownerPrevX
            val dy = owner.y - mind.ownerPrevY
            val dz = owner.z - mind.ownerPrevZ
            val ticks = (owner.sampleTick - mind.ownerPrevTick).coerceAtLeast(1L).toDouble()
            val reach = OWNER_JUMP_BLOCKS * ticks
            if (farBehind && dx * dx + dy * dy + dz * dz > reach * reach) {
                mind.ownerJumped = true
            } else {
                mind.ownerSpeed = mind.ownerSpeed * 0.6 + sqrt(dx * dx + dz * dz) / ticks * 0.4
            }
        }
        if (!farBehind) mind.ownerJumped = false
        mind.ownerPrevX = owner.x
        mind.ownerPrevY = owner.y
        mind.ownerPrevZ = owner.z
        mind.ownerPrevTick = owner.sampleTick
        if (owner.flag(PlayerSignals.IS_MOVING)) {
            mind.ownerStoppedAt = Double.NaN
        } else if (mind.ownerStoppedAt.isNaN()) {
            mind.ownerStoppedAt = now
        }
    }

    private fun sitting(mind: PetMind, owner: PetOwner, now: Double): Boolean =
        !mind.sneakingSince.isNaN() && now - mind.sneakingSince >= SIT_SECONDS && !owner.flag(PlayerSignals.IS_MOVING)

    private fun choose(
        pet: PetManager.Pet,
        asset: PetAsset,
        owner: PetOwner,
        level: ClientLevel,
        now: Double,
        time: Long,
        full: Boolean,
        keep: Boolean,
    ) {
        val mind = pet.body.mind
        val current = mind.goal
        for (goal in URGENT) {
            if (goal == current && keep) return
            if ((goal.priority < current.priority || current.leisure || !keep) && canUse(pet, goal, asset, owner, now, full)) {
                switch(pet.body, goal, now)
                return
            }
        }
        if (full && asset.archetype != PetArchetype.Shoulder && sitting(mind, owner, now)) {
            sitDown(pet.body, now, time)
            return
        }
        val threat = mind.threat
        mind.threat = false
        if (threat && full && current.leisure && current != PetGoal.Look && mind.focus.kind == PetSenses.HOSTILE) {
            switch(pet.body, PetGoal.Look, now)
            mind.leisureUntil = now + 2.0 + mind.random(11, time) * 2.5
            mind.lookReadyAt = mind.leisureUntil + 3.0 + mind.random(12, time) * 5.0
            return
        }
        if (keep) return
        leisure(pet, asset, owner, level, now, time, full)
    }

    private val URGENT = arrayOf(PetGoal.CatchUp, PetGoal.React, PetGoal.Play, PetGoal.Follow)

    private fun sitDown(body: PetBody, now: Double, time: Long) {
        val mind = body.mind
        val hold = now + SNEAK_SIT_HOLD + mind.random(24, time) * SNEAK_SIT_SPAN
        if (mind.goal == PetGoal.Idle && mind.ownerSit) {
            mind.leisureUntil = max(mind.leisureUntil, hold)
            return
        }
        if (mind.goal == PetGoal.Idle) mind.goal = PetGoal.Rest
        switch(body, PetGoal.Idle, now)
        mind.ownerSit = true
        mind.leisureUntil = hold
    }

    private fun switch(body: PetBody, goal: PetGoal, now: Double) {
        val mind = body.mind
        if (mind.goal == goal) return
        mind.goal = goal
        mind.goalSince = now
        mind.hasTarget = false
        mind.perch = false
        mind.pauseUntil = Double.NaN
        mind.action = 0
        mind.sleeping = false
        mind.ownerSit = false
        mind.closing = false
        mind.path.clear()
        if (goal != PetGoal.Play) body.arrived = false
    }

    private fun canUse(pet: PetManager.Pet, goal: PetGoal, asset: PetAsset, owner: PetOwner, now: Double, full: Boolean): Boolean {
        val body = pet.body
        val mind = body.mind
        val leash = asset.leashRadius.toDouble()
        val shoulder = asset.archetype == PetArchetype.Shoulder
        return when (goal) {
            PetGoal.CatchUp -> !shoulder && !waitingForGlide(asset, owner, mind, full) && body.distance > leash * CATCH_UP_LEASHES
            PetGoal.React -> full && reacting(asset, owner, mind, now)
            PetGoal.Play -> body.directive.active
            PetGoal.Follow -> !shoulder && !holdingSit(mind, now) && body.distance > followStart(asset, mind, now)
            else -> true
        }
    }

    private fun holdingSit(mind: PetMind, now: Double): Boolean =
        mind.ownerSit && mind.goal == PetGoal.Idle && now < mind.leisureUntil

    private fun canContinue(pet: PetManager.Pet, asset: PetAsset, owner: PetOwner, now: Double, full: Boolean): Boolean {
        val body = pet.body
        val mind = body.mind
        val leash = asset.leashRadius.toDouble()
        return when (mind.goal) {
            PetGoal.CatchUp -> !waitingForGlide(asset, owner, mind, full) && followGap(body, asset, owner) > followStop(mind, now)
            PetGoal.React -> full && reacting(asset, owner, mind, now)
            PetGoal.Play -> body.directive.active
            PetGoal.Follow -> followGap(body, asset, owner) > followStop(mind, now)
            PetGoal.Look -> full && now < mind.leisureUntil && mind.focus.kind != PetSenses.NONE
            PetGoal.Wander -> full && now < mind.leisureUntil
            PetGoal.Idle -> full && now < mind.leisureUntil
            PetGoal.Rest -> now < mind.leisureUntil
        }
    }

    private fun closer(mind: PetMind, now: Double): Double = if (now < mind.closerUntil) CLOSER_FACTOR else 1.0

    private fun followStart(asset: PetAsset, mind: PetMind, now: Double): Double =
        asset.leashRadius * FOLLOW_START_LEASHES * closer(mind, now)

    private fun followStop(mind: PetMind, now: Double): Double = FOLLOW_STOP * closer(mind, now)

    private fun followY(flying: Boolean, owner: PetOwner, mind: PetMind): Double =
        if (flying) owner.y + mind.flyUp else owner.y

    private fun followGap(body: PetBody, asset: PetAsset, owner: PetOwner): Double {
        val dx = owner.x - body.x
        val dy = followY(asset.archetype == PetArchetype.Flying, owner, body.mind) - body.y
        val dz = owner.z - body.z
        return PetSimulation.gap(dx, dy, dz, asset.archetype != PetArchetype.Flying)
    }

    private fun waitingForGlide(asset: PetAsset, owner: PetOwner, mind: PetMind, full: Boolean): Boolean =
        full && asset.archetype == PetArchetype.Walking && (owner.flag(PlayerSignals.IS_GLIDING) || mind.glideWaiting)

    private fun reacting(asset: PetAsset, owner: PetOwner, mind: PetMind, now: Double): Boolean =
        ownerNeeds(asset, owner, mind, now) || asset.archetype != PetArchetype.Shoulder && mind.glideWaiting

    private fun ownerNeeds(asset: PetAsset, owner: PetOwner, mind: PetMind, now: Double): Boolean {
        if (asset.archetype == PetArchetype.Shoulder) {
            return now < mind.flinchUntil || owner.flag(PlayerSignals.IS_EMOTING)
        }
        return now < mind.flinchUntil ||
            owner.inCombat(now) ||
            owner.flag(PlayerSignals.IS_SLEEPING) ||
            owner.flag(PlayerSignals.IS_GLIDING) ||
            owner.flag(PlayerSignals.IS_EMOTING)
    }

    private fun leisure(
        pet: PetManager.Pet,
        asset: PetAsset,
        owner: PetOwner,
        level: ClientLevel,
        now: Double,
        time: Long,
        full: Boolean,
    ) {
        val body = pet.body
        val mind = body.mind
        if (mind.goal == PetGoal.Idle && mind.sleeping) mind.calmSince = now - IDLE_ACTIONS_AFTER
        val calm = now - mind.calmSince
        val shoulder = asset.archetype == PetArchetype.Shoulder
        if (full && mind.focus.kind != PetSenses.NONE && now >= mind.lookReadyAt) {
            switch(body, PetGoal.Look, now)
            mind.leisureUntil = now + 2.0 + mind.random(11, time) * 2.5
            mind.lookReadyAt = mind.leisureUntil + 3.0 + mind.random(12, time) * 5.0
            return
        }
        if (full && !shoulder && calm >= WANDER_AFTER && calm < SLEEP_AFTER && now >= mind.wanderReadyAt &&
            mind.roll(STROLL_SALT, time) < STROLL_CHANCE && startWander(pet, asset, owner, level, now, time)
        ) {
            return
        }
        if (full && calm >= IDLE_ACTIONS_AFTER) {
            if (mind.goal == PetGoal.Idle) mind.goal = PetGoal.Rest
            switch(body, PetGoal.Idle, now)
            if (asset.archetype == PetArchetype.Flying) groundBelow(body, asset, level)
            if (calm >= SLEEP_AFTER) {
                mind.sleeping = true
                mind.action = 0
                mind.leisureUntil = now + SLEEP_SHORTEST + mind.random(23, time) * SLEEP_SPAN
            } else {
                val roll = mind.random(21, time)
                mind.action = when {
                    roll < 0.35 -> ACTION_SIT
                    roll < 0.6 -> ACTION_LIE
                    roll < 0.85 -> ACTION_GROOM
                    else -> ACTION_YAWN
                }
                val length = when (mind.action) {
                    ACTION_SIT -> 6.0 + mind.random(22, time) * 6.0
                    ACTION_LIE -> 8.0 + mind.random(22, time) * 8.0
                    ACTION_GROOM -> 3.0 + mind.random(22, time) * 2.0
                    else -> 2.0 + mind.random(22, time)
                }
                mind.leisureUntil = now + length
            }
            return
        }
        switch(body, PetGoal.Rest, now)
        mind.leisureUntil = now + 1.0 + mind.random(31, time) * 2.0
    }

    private fun startWander(
        pet: PetManager.Pet,
        asset: PetAsset,
        owner: PetOwner,
        level: ClientLevel,
        now: Double,
        time: Long,
    ): Boolean {
        val body = pet.body
        val mind = body.mind
        val reach = followStart(asset, mind, now) - WANDER_MARGIN
        val halfWidth = PetSimulation.halfWidth(asset)
        val height = PetSimulation.height(asset)
        val flying = asset.archetype == PetArchetype.Flying
        val perch = flying && mind.random(41, time) < PERCH_CHANCE
        for (attempt in 0 until WANDER_TRIES) {
            val angle = mind.random(42L + attempt, time) * PI * 2.0
            val radius = MIN_WANDER + mind.random(50L + attempt, time) * (MAX_WANDER - MIN_WANDER)
            val x = floor(body.x + cos(angle) * radius) + 0.5
            val z = floor(body.z + sin(angle) * radius) + 0.5
            val fromOwnerX = x - owner.x
            val fromOwnerZ = z - owner.z
            if (fromOwnerX * fromOwnerX + fromOwnerZ * fromOwnerZ > reach * reach) continue
            val top = owner.y + if (perch) PERCH_RISE else WANDER_RISE
            val ground = PetCollision.surface(level, x, z, halfWidth, height, top, owner.y - PetPath.MAX_DROP)
            if (ground.isNaN()) continue
            val hazard = PetCollision.hazard(level, x, ground, z, halfWidth, height)
            if (hazard == PetCollision.DANGER || hazard == PetCollision.WATER && !flying) continue
            val y = if (flying && !perch) ground + DRIFT_LOW + mind.random(60L + attempt, time) * DRIFT_SPAN else ground
            switch(body, PetGoal.Wander, now)
            mind.hasTarget = true
            mind.perch = perch
            mind.targetX = x
            mind.targetY = y
            mind.targetZ = z
            mind.leisureUntil = now + WANDER_LIMIT
            mind.gazeUntil = if (mind.random(44, time) < STROLL_GAZE_CHANCE) {
                now + STROLL_GAZE_SECONDS + mind.random(45, time) * STROLL_GAZE_SPAN
            } else {
                0.0
            }
            return true
        }
        mind.wanderReadyAt = now + 1.0
        return false
    }

    private fun groundBelow(body: PetBody, asset: PetAsset, level: ClientLevel) {
        val mind = body.mind
        val halfWidth = PetSimulation.halfWidth(asset)
        val height = PetSimulation.height(asset)
        val ground = PetCollision.surface(
            level,
            body.x,
            body.z,
            halfWidth,
            height,
            body.y,
            body.y - PetPath.MAX_DROP,
        )
        if (ground.isNaN() || PetCollision.hazard(level, body.x, ground, body.z, halfWidth, height) != PetCollision.SAFE) return
        mind.hasTarget = true
        mind.targetX = body.x
        mind.targetY = ground
        mind.targetZ = body.z
    }

    internal fun catchUp(body: PetBody, leash: Double, flying: Boolean, owner: PetOwner, now: Double, steer: PetSteer) {
        val mind = body.mind
        if (mind.ownerJumped && now >= mind.snapRetryAt) {
            mind.snapNow = true
            return
        }
        val speed = if (flying || body.distance > leash * SPRINT_LEASHES) SPRINT else RUN
        steer.go(owner.x, followY(flying, owner, mind), owner.z, speed, followStop(mind, now), true)
        lookAtOwner(owner, steer)
    }

    private fun react(body: PetBody, asset: PetAsset, owner: PetOwner, now: Double, steer: PetSteer) {
        val mind = body.mind
        val flying = asset.archetype == PetArchetype.Flying
        val yaw = Math.toRadians(owner.bodyYaw.toDouble())
        val leftX = cos(yaw)
        val leftZ = sin(yaw)
        val gliding = owner.flag(PlayerSignals.IS_GLIDING)
        when {
            mind.glideWaiting && !gliding -> {
                mind.glideWaiting = false
                mind.snapNow = true
            }
            gliding -> if (flying) {
                steer.go(
                    owner.x + sin(yaw) * SLIPSTREAM_BEHIND,
                    owner.y + SLIPSTREAM_HEIGHT,
                    owner.z - cos(yaw) * SLIPSTREAM_BEHIND,
                    SPRINT * 1.5,
                    0.2,
                    false,
                )
                steer.faceYaw = owner.bodyYaw
            } else {
                mind.glideWaiting = true
                lookAtOwner(owner, steer)
            }
            asset.archetype == PetArchetype.Shoulder -> lookAtOwner(owner, steer)
            owner.flag(PlayerSignals.IS_SLEEPING) -> {
                val x = owner.x + leftX * SLEEP_SIDE
                val z = owner.z + leftZ * SLEEP_SIDE
                steer.go(x, owner.y + if (flying) 0.3 else 0.0, z, WALK, 0.3, true)
                steer.faceYaw = owner.bodyYaw
            }
            now < mind.flinchUntil || owner.flag(PlayerSignals.IS_EMOTING) -> lookAtOwner(owner, steer)
            owner.inCombat(now) -> if (flying) {
                val angle = body.phase + now * CIRCLE_SPEED
                val radius = CIRCLE_RADIUS * if (now < mind.closerUntil) CLOSER_FACTOR else 1.0
                steer.go(owner.x + cos(angle) * radius, owner.y + CIRCLE_HEIGHT + bob(body), owner.z + sin(angle) * radius, RUN, 0.1, false)
                lookAhead(body, owner, steer)
            } else {
                val gap = followGap(body, asset, owner)
                if (mind.closing && gap <= COMBAT_STOP) mind.closing = false
                if (!mind.closing && gap > asset.leashRadius * COMBAT_START_LEASHES) mind.closing = true
                if (mind.closing) steer.go(owner.x, owner.y, owner.z, WALK, COMBAT_STOP, true)
                val focus = mind.focus
                if (focus.kind == PetSenses.HOSTILE) steer.look(focus.x, focus.y, focus.z) else lookAtOwner(owner, steer)
            }
        }
    }

    private fun play(pet: PetManager.Pet, asset: PetAsset, now: Double, steer: PetSteer) {
        val body = pet.body
        val directive = body.directive
        val partner = directive.partner
        if (partner != null && partner.present) steer.look(partner.body.x, partner.body.y + 0.3, partner.body.z)
        if (asset.archetype == PetArchetype.Shoulder) {
            body.arrived = true
            return
        }
        val flying = asset.archetype == PetArchetype.Flying
        if (!directive.started) {
            body.arrived = holdMark(body, directive, flying, steer)
            return
        }
        val elapsed = now - directive.startedAt
        when (directive.interaction) {
            PetInteraction.Follow -> if (!directive.authored && directive.role == PetInteractions.ROLE_A) {
                val angle = directive.circlePhase + elapsed * FOLLOW_CIRCLE_SPEED
                steer.go(
                    directive.circleX + cos(angle) * directive.circleRadius,
                    directive.markY,
                    directive.circleZ + sin(angle) * directive.circleRadius,
                    WALK * 1.3,
                    0.05,
                    false,
                )
            } else {
                steer.faceYaw = directive.faceYaw
            }
            PetInteraction.Chase -> if (directive.authored) {
                steer.faceYaw = directive.faceYaw
            } else {
                val runner = (floor(elapsed / CHASE_SWAP_SECONDS).toLong() and 1L).toInt()
                if (directive.role == runner || partner == null || !partner.present) {
                    val omega = RUN / directive.circleRadius * TICKS_PER_SECOND
                    val angle = directive.circlePhase + directive.spin * elapsed * omega
                    val lift = if (flying) 1.0 + bob(body) else 0.0
                    steer.go(
                        directive.circleX + cos(angle) * directive.circleRadius,
                        directive.markY + lift,
                        directive.circleZ + sin(angle) * directive.circleRadius,
                        RUN,
                        0.05,
                        false,
                    )
                } else {
                    steer.go(partner.body.x, partner.body.y, partner.body.z, RUN * CHASER_PACE, CHASER_GAP, false)
                }
            }
            PetInteraction.Play -> {
                steer.faceYaw = directive.faceYaw
                if (!directive.authored) hop(body, flying, directive, elapsed, steer)
            }
            PetInteraction.Greet -> holdMark(body, directive, flying, steer)
            else -> steer.faceYaw = directive.faceYaw
        }
    }

    internal fun holdMark(body: PetBody, directive: PetDirective, flying: Boolean, steer: PetSteer): Boolean {
        val dx = directive.markX - body.x
        val dy = directive.markY - body.y
        val dz = directive.markZ - body.z
        val arrived = if (flying) {
            dx * dx + dy * dy + dz * dz <= MARK_ARRIVED * MARK_ARRIVED
        } else {
            dx * dx + dz * dz <= MARK_ARRIVED * MARK_ARRIVED && abs(dy) <= PetSimulation.CLIMB_SLACK
        }
        if (arrived) {
            steer.faceYaw = directive.faceYaw
        } else {
            steer.go(directive.markX, directive.markY, directive.markZ, WALK * 1.2, 0.15, true)
            steer.climb = true
        }
        return arrived
    }

    private fun hop(body: PetBody, flying: Boolean, directive: PetDirective, elapsed: Double, steer: PetSteer) {
        val beat = elapsed / HOP_SECONDS
        if (flying) {
            val lift = abs(sin(beat * PI)) * HOP_LIFT
            steer.go(directive.markX, directive.markY + lift, directive.markZ, FLY, 0.02, false)
            steer.faceYaw = directive.faceYaw
            return
        }
        val previous = (elapsed - PetSimulation.TICK_SECONDS) / HOP_SECONDS
        if (elapsed >= 0.0 && floor(beat) != floor(previous)) steer.hop = true
    }

    internal fun follow(body: PetBody, flying: Boolean, owner: PetOwner, now: Double, steer: PetSteer) {
        val mind = body.mind
        val speed = if (flying) FLY else WALK
        steer.go(owner.x, followY(flying, owner, mind), owner.z, speed, followStop(mind, now), true)
        lookAtOwner(owner, steer)
    }

    private fun look(body: PetBody, asset: PetAsset, owner: PetOwner, steer: PetSteer) {
        val mind = body.mind
        val focus = mind.focus
        steer.look(focus.x, focus.y, focus.z)
        if (focus.kind != PetSenses.HOSTILE || asset.archetype != PetArchetype.Walking) return
        val dx = body.x - focus.x
        val dz = body.z - focus.z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance >= BACK_AWAY || distance < 1.0e-3) return
        var tx = body.x + dx / distance * BACK_AWAY_STEP
        var tz = body.z + dz / distance * BACK_AWAY_STEP
        val ox = tx - owner.x
        val oz = tz - owner.z
        val reach = followStart(asset, mind, body.lastTick) - WANDER_MARGIN
        val fromOwner = sqrt(ox * ox + oz * oz)
        if (fromOwner > reach) {
            tx = owner.x + ox / fromOwner * reach
            tz = owner.z + oz / fromOwner * reach
        }
        steer.go(tx, body.y, tz, WALK, 0.3, false)
        steer.faceYaw = Float.NaN
    }

    private fun wander(
        body: PetBody,
        asset: PetAsset,
        now: Double,
        time: Long,
        steer: PetSteer,
    ) {
        val mind = body.mind
        if (mind.pauseUntil.isNaN()) {
            val dx = mind.targetX - body.x
            val dy = mind.targetY - body.y
            val dz = mind.targetZ - body.z
            val close = dx * dx + dz * dz < 0.25 && (asset.archetype != PetArchetype.Flying || dy * dy < 0.09)
            val stuck = now - mind.goalSince > WANDER_LIMIT - PAUSE_LONGEST || mind.path.state == PetPath.State.Failed
            if (!close && !stuck) {
                val speed = if (asset.archetype == PetArchetype.Flying) STROLL * 1.2 else STROLL
                steer.go(mind.targetX, mind.targetY, mind.targetZ, speed, 0.15, true)
                steer.land = mind.perch
                val focus = mind.focus
                if (now < mind.gazeUntil && focus.kind != PetSenses.NONE) steer.look(focus.x, focus.y, focus.z)
                return
            }
            val roll = mind.random(81, time)
            mind.pauseUntil = now + if (mind.perch) {
                PERCH_SHORTEST + mind.random(82, time) * PERCH_SPAN
            } else {
                PAUSE_SHORTEST + mind.random(82, time) * (PAUSE_LONGEST - PAUSE_SHORTEST)
            }
            mind.action = when {
                mind.perch && close -> ACTION_PERCH
                asset.archetype == PetArchetype.Flying -> 0
                roll < 0.45 -> ACTION_SNIFF
                roll < 0.65 -> ACTION_SIT
                else -> 0
            }
            mind.leisureUntil = max(mind.leisureUntil, mind.pauseUntil + 0.05)
        }
        if (now >= mind.pauseUntil) {
            mind.leisureUntil = now
            mind.wanderReadyAt = now + if (now - mind.calmSince >= IDLE_ACTIONS_AFTER) {
                8.0 + mind.random(83, time) * 12.0
            } else {
                1.0 + mind.random(83, time) * 3.0
            }
            return
        }
        if (mind.action == ACTION_PERCH) {
            steer.go(mind.targetX, mind.targetY, mind.targetZ, STROLL, 0.0, false)
            steer.land = true
        }
        if (mind.action == ACTION_SNIFF) steer.glanceYaw = body.yaw
    }

    private fun idle(body: PetBody, owner: PetOwner, now: Double, steer: PetSteer) {
        val mind = body.mind
        if (mind.ownerSit) {
            if (sitting(mind, owner, now)) lookAtOwner(owner, steer)
            return
        }
        if (mind.hasTarget && (mind.sleeping || mind.action == ACTION_LIE || mind.action == ACTION_SIT)) {
            steer.go(mind.targetX, mind.targetY, mind.targetZ, STROLL, 0.0, false)
            steer.land = true
        }
        if (mind.sleeping) {
            steer.glanceYaw = body.yaw
            return
        }
        val focus = mind.focus
        if (focus.kind != PetSenses.NONE && mind.action != ACTION_GROOM && mind.action != ACTION_YAWN) {
            steer.look(focus.x, focus.y, focus.z)
        } else {
            steer.glanceYaw = body.yaw
        }
    }

    private fun glance(body: PetBody, mind: PetMind, now: Double, time: Long, steer: PetSteer) {
        if (body.gait || steer.moving) {
            mind.glanceYaw = Float.NaN
            mind.glanceNext = max(mind.glanceNext, now + LOOK_AROUND_GAP)
            return
        }
        if (!mind.glanceYaw.isNaN() && now >= mind.glanceUntil) {
            mind.glanceYaw = Float.NaN
            mind.glanceNext = now + LOOK_AROUND_GAP + mind.roll(LOOK_AROUND_SALT, time) * LOOK_AROUND_GAP_SPAN
        }
        if (mind.glanceYaw.isNaN() && now >= mind.glanceNext) {
            mind.glanceYaw = (mind.roll(LOOK_AROUND_SALT + 1, time) * 360.0 - 180.0).toFloat()
            mind.glanceUntil = now + LOOK_AROUND_SECONDS + mind.roll(LOOK_AROUND_SALT + 2, time) * LOOK_AROUND_SPAN
        }
        if (!mind.glanceYaw.isNaN()) steer.glanceYaw = mind.glanceYaw
    }

    private fun attend(
        pet: PetManager.Pet,
        mind: PetMind,
        owner: PetOwner,
        level: ClientLevel,
        now: Double,
        time: Long,
        pets: List<PetManager.Pet>,
    ) {
        val body = pet.body
        val focus = mind.focus
        if (focus.kind == PetSenses.OWNER) {
            focus.x = owner.x
            focus.y = owner.y + owner.eyeHeight
            focus.z = owner.z
        } else if (focus.kind != PetSenses.NONE && !PetSenses.track(focus)) {
            focus.clear()
        }
        if (focus.kind != PetSenses.NONE && distanceSq(body, focus) > LOOK_RADIUS * LOOK_RADIUS * 1.5) focus.clear()
        if (body.ticks < mind.nextScan) return
        mind.nextScan = body.ticks + SCAN_TICKS
        var count = PetSenses.scan(level, body.x, body.y, body.z, LOOK_RADIUS, pet.owner, mind.sights)
        for (index in pets.indices) {
            if (count >= mind.sights.size) break
            val other = pets[index]
            if (other === pet || !other.present || other.asset == null) continue
            val dx = other.body.x - body.x
            val dz = other.body.z - body.z
            if (dx * dx + dz * dz > LOOK_RADIUS * LOOK_RADIUS) continue
            val sight = mind.sights[count++]
            sight.clear()
            sight.kind = PetSenses.PET
            sight.pet = other
            PetSenses.track(sight)
        }
        if (count < mind.sights.size) {
            val sight = mind.sights[count++]
            sight.clear()
            sight.kind = PetSenses.OWNER
            sight.x = owner.x
            sight.y = owner.y + owner.eyeHeight
            sight.z = owner.z
        }
        mind.sightCount = count
        var alarm = false
        for (i in 0 until count) {
            val sight = mind.sights[i]
            if (sight.kind == PetSenses.HOSTILE && distanceSq(body, sight) < HOSTILE_ALERT * HOSTILE_ALERT) alarm = true
        }
        val fresh = alarm && !mind.alarmed
        mind.alarmed = alarm
        if (now < mind.focusUntil && focus.kind != PetSenses.NONE && (!alarm || focus.kind == PetSenses.HOSTILE)) {
            if (fresh && focus.kind == PetSenses.HOSTILE) mind.threat = true
            return
        }
        var best = -1
        var bestScore = MIN_INTEREST
        for (i in 0 until count) {
            val sight = mind.sights[i]
            if (alarm && sight.kind != PetSenses.HOSTILE) continue
            val distance = sqrt(distanceSq(body, sight))
            val base = when (sight.kind) {
                PetSenses.OWNER -> if (!mind.ownerStoppedAt.isNaN() && now - mind.ownerStoppedAt < OWNER_STOPPED_INTEREST) 6.0 else 1.5
                PetSenses.PLAYER -> if (distance < CLOSE_PLAYER) 5.0 else 2.5
                PetSenses.PET -> 3.0
                PetSenses.ITEM -> 3.5
                PetSenses.HOSTILE -> 5.5
                else -> 2.0
            }
            val score = base - distance * DISTANCE_PENALTY + mind.random(100L + i, time) * NOISE
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        if (best < 0) {
            focus.clear()
            return
        }
        focus.copy(mind.sights[best])
        mind.focusUntil = now + 2.0 + mind.random(120, time) * 3.0
        if (fresh && focus.kind == PetSenses.HOSTILE) mind.threat = true
    }

    private fun bob(body: PetBody): Double = sin((body.ticks + body.phase * FLY_BOB_PERIOD) / FLY_BOB_PERIOD) * FLY_BOB_HEIGHT

    private fun lookAtOwner(owner: PetOwner, steer: PetSteer) {
        steer.look(owner.x, owner.y + owner.eyeHeight, owner.z)
    }

    private fun lookAhead(body: PetBody, owner: PetOwner, steer: PetSteer) {
        if (body.gait) {
            steer.glanceYaw = body.yaw
        } else {
            lookAtOwner(owner, steer)
        }
    }

    private fun distanceSq(body: PetBody, sight: PetSight): Double {
        val dx = sight.x - body.x
        val dy = sight.y - body.y
        val dz = sight.z - body.z
        return dx * dx + dy * dy + dz * dz
    }

    const val ACTION_SIT = 1
    const val ACTION_LIE = 2
    const val ACTION_GROOM = 3
    const val ACTION_YAWN = 4
    const val ACTION_SNIFF = 5
    const val ACTION_PERCH = 6

    private const val MOOD_HURT = -0.2f
    private const val TICKS_PER_SECOND = 20.0
    private const val CHASER_PACE = 0.92
    private const val CHASER_GAP = 0.7
    private const val MIN_INTEREST = 0.5
    private const val DISTANCE_PENALTY = 0.25
    private const val NOISE = 2.0
    private const val WANDER_TRIES = 6
    private const val MIN_WANDER = 2.0
    private const val MAX_WANDER = 5.0
    private const val STROLL_CHANCE = 0.3
    private const val STROLL_SALT = 43L
    private const val STROLL_GAZE_CHANCE = 0.4
    private const val STROLL_GAZE_SECONDS = 1.5
    private const val STROLL_GAZE_SPAN = 2.0
    private const val SNEAK_SIT_HOLD = 6.0
    private const val SNEAK_SIT_SPAN = 6.0
    private const val SLEEP_SHORTEST = 90.0
    private const val SLEEP_SPAN = 90.0
    private const val LOOK_AROUND_SALT = 93L
    private const val LOOK_AROUND_GAP = 2.0
    private const val LOOK_AROUND_GAP_SPAN = 6.0
    private const val LOOK_AROUND_SECONDS = 1.0
    private const val LOOK_AROUND_SPAN = 1.0
    private const val WANDER_RISE = 2.0
    private const val PERCH_RISE = 4.0
    private const val PERCH_CHANCE = 0.35
    private const val DRIFT_LOW = 1.0
    private const val DRIFT_SPAN = 2.0
    private const val WANDER_LIMIT = 16.0
    private const val PAUSE_SHORTEST = 2.0
    private const val PAUSE_LONGEST = 6.0
    private const val PERCH_SHORTEST = 5.0
    private const val PERCH_SPAN = 7.0
}
