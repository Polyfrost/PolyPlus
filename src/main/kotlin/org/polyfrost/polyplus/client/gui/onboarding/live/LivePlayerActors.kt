package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
//? if >= 1.21.10 {
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?} elif >= 1.21.4 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState as AvatarRenderState
*///?}
//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.HorseRenderState
//?}
//? if >= 1.21.5 {
import net.minecraft.world.entity.EquipmentSlot
//?}
//? if = 1.21.1 {
/*import net.minecraft.world.entity.animal.horse.Variant
*///?}

internal class LivePlayerActor(val tweak: LiveTweak, private val ours: Boolean) {
    @Volatile
    var place: LiveSpotPlace? = null

    var eye: Vec3 = Vec3.ZERO

    var player: LivePlayerAvatar? = null
        private set
    var horse: LivePlayerHorse? = null
        private set
    var keyDown = false
        private set

    private var level: ClientLevel? = null
    private var physics = false
    private var runTicks = 0
    private var cyclePhase = 0
    private var placedAt: Vec3? = null
    private val cycle: Int = when (tweak) {
        LiveTweak.SPRINT -> SPRINT_CYCLE
        LiveTweak.CAPES -> CAPES_CYCLE
        LiveTweak.MOUNT -> MOUNT_CYCLE
        else -> 0
    }
    private var velocity = 0.0
    private var horseVelocity = 0.0
    private var fov = 1f
    private var fovO = 1f

    var keyDownState by mutableStateOf(false)
        private set
    var sprintingState by mutableStateOf(false)
        private set

    val physicsLit: Boolean get() = physics

    fun tick() {
        runCatching { step() }.onFailure { LivePlayerAvatars.warn("tick", it) }
    }

    private fun step() {
        val place = place ?: return
        val wanted = LivePlayerAvatars.lobbyLevel() ?: LivePlayerAvatars.level() ?: return
        if (wanted !== level || player == null) spawn(wanted, place)
        val p = player ?: return
        if (placedAt != place.feet) restart(place)
        if (cycle > 0) {
            val next = cycleTick()
            val wrapped = next < cyclePhase
            cyclePhase = next
            if (wrapped) restart(place)
        }
        runTicks++
        drive(p)
        if (tweak != LiveTweak.SKIN_LAYERS && progress(place) > runLength(place)) p.zza = 0f
        val moved = runCatching { if (physics) tickPhysics(p) else tickKinematic(p, place) }
        moved.onFailure {
            LivePlayerAvatars.warn("physics", it)
            physics = false
        }
        if (physics && p.y < place.feet.y - FALL_LIMIT) {
            physics = false
            restart(place)
        }
        fovO = fov
        //? if >= 1.21.4 {
        fov += (runCatching { p.getFieldOfViewModifier(false, 1f) }.getOrDefault(1f) - fov) * FOV_EASE
        //?} else {
        /*fov += (runCatching { p.fieldOfViewModifier }.getOrDefault(1f) - fov) * FOV_EASE
        *///?}
        keyDownState = keyDown
        sprintingState = p.isSprinting
    }

    private fun cycleTick(): Int = ((LivePlayerScene.ticks - LivePlayerScene.groupStart(tweak) + cycleOffset()) % cycle).toInt()

    private fun cycleOffset(): Long = if (tweak == LiveTweak.SPRINT) SPRINT_OFFSET else 0L

    fun release() {
        horse?.let { h -> player?.let { if (it.vehicle === h) it.stopRiding() } }
        player = null
        horse = null
        level = null
    }

    fun fadeAlpha(partial: Float): Float {
        if (cycle <= 0) return 0f
        val f = cyclePhase + partial
        return when {
            f >= cycle - FADE_IN -> ((f - (cycle - FADE_IN)) / FADE_IN).coerceIn(0f, 1f)
            f < FADE_OUT -> (1f - f / FADE_OUT).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    fun fovScale(partial: Float): Float = Mth.lerp(partial, fovO, fov)

    fun position(entity: LivingEntity, partial: Float): Vec3 = Vec3(
        Mth.lerp(partial.toDouble(), entity.xo, entity.x),
        Mth.lerp(partial.toDouble(), entity.yo, entity.y),
        Mth.lerp(partial.toDouble(), entity.zo, entity.z),
    )

    private fun spawn(target: ClientLevel, place: LiveSpotPlace) {
        release()
        level = target
        physics = target === LivePlayerAvatars.lobbyLevel()
        player = LivePlayerAvatars.player(target)
        if (tweak == LiveTweak.MOUNT) horse = LivePlayerAvatars.horse(target)
        cyclePhase = if (cycle > 0) cycleTick() else 0
        horse?.let { h ->
            runCatching {
                h.setTamed(true)
                //? if >= 1.21.5 {
                h.setItemSlot(EquipmentSlot.SADDLE, ItemStack(Items.SADDLE))
                //?} else {
                /*h.equipSaddle(ItemStack(Items.SADDLE), null)
                *///?}
                //? if = 1.21.1 {
                /*h.setVariant(Variant.CHESTNUT)
                *///?}
            }.onFailure { LivePlayerAvatars.warn("saddle", it) }
        }
        restart(place)
    }

    private fun restart(place: LiveSpotPlace) {
        val p = player ?: return
        val start = if (tweak == LiveTweak.SKIN_LAYERS) place.feet else place.ahead(PATH_MARGIN)
        placedAt = place.feet
        runTicks = if (cycle > 0) cyclePhase else 0
        velocity = 0.0
        horseVelocity = 0.0
        p.isSprinting = false
        p.zza = 0f
        p.xxa = 0f
        val h = horse
        if (h != null) {
            //? if >= 1.21.10 {
            if (p.vehicle !== h) runCatching { p.startRiding(h, true, false) }.onFailure { LivePlayerAvatars.warn("riding", it) }
            //?} else {
            /*if (p.vehicle !== h) runCatching { p.startRiding(h, true) }.onFailure { LivePlayerAvatars.warn("riding", it) }
            *///?}
            place(h, start, place.yaw)
        }
        place(p, if (h != null) start.add(0.0, RIDER_OFFSET, 0.0) else start, place.yaw)
        p.xRot = if (h != null) RIDE_PITCH else 0f
        p.xRotO = p.xRot
    }

    private fun place(entity: LivingEntity, pos: Vec3, yaw: Float) {
        //? if >= 1.21.5 {
        entity.snapTo(pos.x, pos.y, pos.z, yaw, entity.xRot)
        //?} else {
        /*entity.moveTo(pos.x, pos.y, pos.z, yaw, entity.xRot)
        *///?}
        entity.setOldPosAndRot()
        entity.deltaMovement = Vec3.ZERO
        entity.yBodyRot = yaw
        entity.yBodyRotO = yaw
        entity.yHeadRot = yaw
        entity.yHeadRotO = yaw
        //? if >= 1.21.4 {
        entity.walkAnimation.stop()
        //?} else {
        /*entity.walkAnimation.setSpeed(0f)
        *///?}
    }

    private fun drive(p: LivePlayerAvatar) {
        val forward = tweak != LiveTweak.SKIN_LAYERS
        p.driving = forward && horse == null
        p.zza = if (!forward) 0f else if (horse != null) RIDE_INPUT else FORWARD_INPUT
        p.xxa = 0f
        place?.let {
            p.yRot = it.yaw
            p.yHeadRot = it.yaw
        }
        keyDown = false
        if (tweak != LiveTweak.SPRINT) return
        val pressed = runTicks >= SPRINT_PRESS
        val release = if (ours) SPRINT_PRESS + TAP_TICKS else SPRINT_PRESS + HOLD_TICKS
        keyDown = pressed && runTicks < release
        p.wantSprint = if (ours) pressed else keyDown
        p.isSprinting = p.wantSprint
    }

    private fun tickPhysics(p: LivePlayerAvatar) {
        val h = horse
        if (h != null) {
            h.setOldPosAndRot()
            h.tickCount++
            h.tick()
            p.setOldPosAndRot()
            p.tickCount++
            p.rideTick()
            return
        }
        p.setOldPosAndRot()
        p.tickCount++
        if (tweak == LiveTweak.CAPES) LivePlayerMods.withWaveyCapes(true) { p.tick() } else p.tick()
    }

    private fun tickKinematic(p: LivePlayerAvatar, place: LiveSpotPlace) {
        val rad = Math.toRadians(place.yaw.toDouble())
        val dirX = -sin(rad)
        val dirZ = cos(rad)
        val h = horse
        p.setOldPosAndRot()
        p.tickCount++
        if (h != null) {
            h.setOldPosAndRot()
            h.tickCount++
            horseVelocity = step(horseVelocity, HORSE_SPEED * p.zza / FORWARD_INPUT)
            h.setPos(h.x + dirX * horseVelocity, place.feet.y, h.z + dirZ * horseVelocity)
            h.calculateEntityAnimation(false)
            p.setPos(h.x, h.y + RIDER_OFFSET, h.z)
            return
        }
        val speed = if (p.zza <= 0f) 0.0 else if (p.isSprinting) SPRINT_SPEED else WALK_SPEED
        velocity = step(velocity, speed)
        val delta = Vec3(dirX * velocity, 0.0, dirZ * velocity)
        //? if >= 1.21.10 {
        p.avatarState().tick(p.position(), delta)
        p.setPos(p.x + delta.x, place.feet.y, p.z + delta.z)
        p.deltaMovement = delta
        p.avatarState().addWalkDistance((velocity * WALK_DISTANCE_SCALE).toFloat())
        p.avatarState().updateBob(min(MAX_BOB, velocity.toFloat()))
        //?} else {
        /*p.walkDistO = p.walkDist
        moveCloak(p)
        p.setPos(p.x + delta.x, place.feet.y, p.z + delta.z)
        p.deltaMovement = delta
        p.walkDist += (velocity * WALK_DISTANCE_SCALE).toFloat()
        p.oBob = p.bob
        p.bob += (min(MAX_BOB, velocity.toFloat()) - p.bob) * BOB_EASE
        *///?}
        p.calculateEntityAnimation(false)
        if (tweak == LiveTweak.CAPES) LivePlayerMods.stepCape(p)
    }

    //? if < 1.21.10 {
    /*private fun moveCloak(p: LivePlayerAvatar) {
        p.xCloakO = p.xCloak
        p.yCloakO = p.yCloak
        p.zCloakO = p.zCloak
        val dx = p.x - p.xCloak
        val dy = p.y - p.yCloak
        val dz = p.z - p.zCloak
        if (dx > CLOAK_SNAP || dx < -CLOAK_SNAP) {
            p.xCloak = p.x
            p.xCloakO = p.xCloak
        } else {
            p.xCloak += dx * CLOAK_EASE
        }
        if (dy > CLOAK_SNAP || dy < -CLOAK_SNAP) {
            p.yCloak = p.y
            p.yCloakO = p.yCloak
        } else {
            p.yCloak += dy * CLOAK_EASE
        }
        if (dz > CLOAK_SNAP || dz < -CLOAK_SNAP) {
            p.zCloak = p.z
            p.zCloakO = p.zCloak
        } else {
            p.zCloak += dz * CLOAK_EASE
        }
    }
    *///?}

    private fun step(current: Double, speed: Double): Double = (current + speed * FORWARD_INPUT) * GROUND_DRAG

    private fun progress(place: LiveSpotPlace): Double {
        val p = horse ?: player ?: return 0.0
        val rad = Math.toRadians(place.yaw.toDouble())
        val start = place.ahead(PATH_MARGIN)
        return (p.x - start.x) * -sin(rad) + (p.z - start.z) * cos(rad)
    }

    private fun runLength(place: LiveSpotPlace): Double = (place.length - PATH_MARGIN * 2).coerceAtLeast(1.0)

    fun extract(entity: LivingEntity, partial: Float): LiveDraw? {
        if (physics) {
            //? if >= 1.21.10 {
            val dispatcher = Minecraft.getInstance().entityRenderDispatcher
            val camera = dispatcher.camera
            dispatcher.camera = null
            return try {
                runCatching { dispatcher.extractEntity(entity, partial) }
                    .onFailure { LivePlayerAvatars.warn("extract", it) }
                    .getOrNull()
            } finally {
                dispatcher.camera = camera
            }
            //?} elif >= 1.21.4 {
            /*val state = LivePlayerRenderer.withStandInCamera {
                runCatching { LivePlayerRenderer.rendererOf(entity).createRenderState(entity, partial) }
                    .onFailure { LivePlayerAvatars.warn("extract", it) }
                    .getOrNull()
            } ?: return null
            state.nameTag = null
            state.distanceToCameraSq = eye.distanceToSqr(state.x, state.y, state.z)
            return LiveDraw(state, entity, partial)
            *///?} else {
            /*return LiveDraw(entity, partial)
            *///?}
        }
        //? if >= 1.21.10 {
        return when (entity) {
            is LivePlayerAvatar -> manualAvatar(entity, partial)
            is LivePlayerHorse -> manualHorse(entity, partial)
            else -> null
        }
        //?} elif >= 1.21.4 {
        /*val state = when (entity) {
            is LivePlayerAvatar -> manualAvatar(entity, partial)
            is LivePlayerHorse -> manualHorse(entity, partial)
            else -> null
        } ?: return null
        return LiveDraw(state, entity, partial)
        *///?} else {
        /*return LiveDraw(entity, partial)
        *///?}
    }

    //? if >= 1.21.4 {
    private fun manualAvatar(p: LivePlayerAvatar, partial: Float): AvatarRenderState = LivePlayerRenderer.avatarState().apply {
        skin = p.skin
        ageInTicks = p.tickCount + partial
        bodyRot = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot)
        walkAnimationPos = p.walkAnimation.position(partial)
        walkAnimationSpeed = p.walkAnimation.speed(partial)
        isPassenger = p.isPassenger
        //? if >= 1.21.10 {
        val cloak = p.avatarState()
        val deltaX = cloak.getInterpolatedCloakX(partial) - Mth.lerp(partial.toDouble(), p.xo, p.x)
        val deltaY = cloak.getInterpolatedCloakY(partial) - Mth.lerp(partial.toDouble(), p.yo, p.y)
        val deltaZ = cloak.getInterpolatedCloakZ(partial) - Mth.lerp(partial.toDouble(), p.zo, p.z)
        //?} else {
        /*val deltaX = Mth.lerp(partial.toDouble(), p.xCloakO, p.xCloak) - Mth.lerp(partial.toDouble(), p.xo, p.x)
        val deltaY = Mth.lerp(partial.toDouble(), p.yCloakO, p.yCloak) - Mth.lerp(partial.toDouble(), p.yo, p.y)
        val deltaZ = Mth.lerp(partial.toDouble(), p.zCloakO, p.zCloak) - Mth.lerp(partial.toDouble(), p.zo, p.z)
        *///?}
        val body = Math.toRadians(bodyRot.toDouble())
        val forwardX = sin(body)
        val forwardZ = -cos(body)
        capeFlap = Mth.clamp(deltaY.toFloat() * CAPE_FLAP_SCALE, CAPE_FLAP_MIN, CAPE_FLAP_MAX)
        capeLean = Mth.clamp((deltaX * forwardX + deltaZ * forwardZ).toFloat() * CAPE_LEAN_SCALE, 0f, CAPE_LEAN_MAX)
        capeLean2 = Mth.clamp((deltaX * forwardZ - deltaZ * forwardX).toFloat() * CAPE_LEAN_SCALE, -CAPE_SIDE_MAX, CAPE_SIDE_MAX)
        //? if >= 1.21.10 {
        val walked = cloak.getInterpolatedWalkDistance(partial)
        val bob = cloak.getInterpolatedBob(partial)
        //?} else {
        /*val walked = Mth.lerp(partial, p.walkDistO, p.walkDist)
        val bob = Mth.lerp(partial, p.oBob, p.bob)
        *///?}
        capeFlap += sin(walked * CAPE_WALK_FREQUENCY).toFloat() * CAPE_WALK_FLAP * bob
        LivePlayerMods.attachEntity(this, p)
    }

    private fun manualHorse(h: LivePlayerHorse, partial: Float): HorseRenderState = LivePlayerRenderer.horseState().apply {
        ageInTicks = h.tickCount + partial
        bodyRot = Mth.rotLerp(partial, h.yBodyRotO, h.yBodyRot)
        walkAnimationPos = h.walkAnimation.position(partial)
        walkAnimationSpeed = h.walkAnimation.speed(partial)
        //? if >= 1.21.5 {
        saddle = runCatching { h.getItemBySlot(EquipmentSlot.SADDLE).copy() }.getOrDefault(ItemStack.EMPTY)
        //?} else {
        /*isSaddled = runCatching { h.isSaddled }.getOrDefault(false)
        *///?}
    }
    //?}

    private companion object {
        const val FADE_IN = 4
        const val FADE_OUT = 6
        const val SPRINT_CYCLE = 85
        const val CAPES_CYCLE = 100
        const val MOUNT_CYCLE = 180
        const val PATH_MARGIN = 3.0
        const val FALL_LIMIT = 4.0
        const val FORWARD_INPUT = 0.98f
        const val RIDE_INPUT = 0.26f
        const val RIDER_OFFSET = 0.875
        const val RIDE_PITCH = 0f
        const val SPRINT_PRESS = 20
        const val SPRINT_OFFSET = 45L
        const val TAP_TICKS = 4
        const val HOLD_TICKS = 40
        const val FOV_EASE = 0.5f
        const val WALK_SPEED = 0.1
        const val SPRINT_SPEED = 0.13
        const val HORSE_SPEED = 0.225
        const val GROUND_DRAG = 0.546
        const val WALK_DISTANCE_SCALE = 0.6
        const val MAX_BOB = 0.1f
        const val CAPE_FLAP_SCALE = 10f
        const val CAPE_FLAP_MIN = -6f
        const val CAPE_FLAP_MAX = 32f
        const val CAPE_LEAN_SCALE = 100f
        const val CAPE_LEAN_MAX = 150f
        const val CAPE_SIDE_MAX = 20f
        const val CAPE_WALK_FREQUENCY = 6.0
        const val CAPE_WALK_FLAP = 32f
        //? if < 1.21.10 {
        /*const val BOB_EASE = 0.4f
        const val CLOAK_SNAP = 10.0
        const val CLOAK_EASE = 0.25
        *///?}
    }
}
//?}
