@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.polyplus.client.lobby.LobbyEase
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.Easing
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.world.entity.LivingEntity
//?} else {
/*import net.minecraft.client.gui.screen.game.DeathScreen
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.entity.living.LivingEntity
*///?}

object PauseStage {
    private const val GLIDE_TIME = 0.45f
    private const val MIN_GLIDE = 0.12f
    private const val HAND_SHARE = 0.12f
    private const val CURTAIN_SECONDS = 0.14f
    private const val REDUCED_MOTION = 0.5f
    private const val MAX_FRAME = 0.1f
    private const val FIRST_FRAME = 0.008f

    private const val REACH = 2.8
    private const val MIN_ROOM = 1.8
    private const val WALL_MARGIN = 0.3
    private const val SHOT_SHARE = 0.56
    private const val MIN_LENS = 50f
    private const val MAX_LENS = 80f
    private const val SCREEN_X = -0.3
    private const val CHEST_SHARE = 0.56
    private const val CAMERA_SHARE = 0.72
    private const val ARC_START = 120.0
    private const val ORBIT_MIN = 0.3
    private const val AIM_SHARE = 0.5
    private const val TURN_LEAD = 1.37f
    private const val BODY_CLEAR = 0.35
    private const val BODY_HIDE = 0.45
    private const val CLEAR_EPSILON = 0.001
    private const val MIDDLE_SHARE = 0.6
    private const val WIDTH_PROBE = 1.5
    private const val MIN_WIDTH = 2.2
    private const val HEADROOM = 0.9
    private const val AVATAR_START = 0.1f
    private const val AVATAR_SHARE = 0.5f
    private const val TAG_LIFT = 0.42

    private const val GLIDE_DEGREES = 5.0
    private const val GLIDE_PUSH = 0.07
    private const val GLIDE_SECONDS = 14.0
    private const val PARALLAX_YAW = 1.3
    private const val PARALLAX_RISE = 0.05
    private const val PARALLAX_RATE = 3.0
    private const val ROOM_RATE = 6.0
    private const val LENS_RATE = 5.0

    private val SIDES = floatArrayOf(38f, 62f, 95f)

    class Pose(
        @JvmField val x: Double,
        @JvmField val y: Double,
        @JvmField val z: Double,
        @JvmField val yaw: Float,
        @JvmField val pitch: Float,
    )

    private enum class Phase { IDLE, STAGED, RETURNING }

    private var phase = Phase.IDLE
    private var cut = false
    private var seconds = 0f
    private var amount = 0f
    private var moveFrom = 0f
    private var moveTo = 0f
    private var moveElapsed = 0f
    private var moveTime = GLIDE_TIME
    private var fresh = false
    private var lastNanos = 0L
    private var frameSeconds = 0f
    private var eased = false
    private var side = SIDES[0]
    private var reach = REACH
    private var fullReach = REACH
    private var rawFov = 70f
    private var worldPass = true
    private val lens = LobbyEase(60.0)
    private var fovNow = 0f
    private var motion = 1f
    private var posed = false
    private var savedMode = 0
    private var switched = false
    private var savedHud = false
    private var hudForced = false
    private var actor: LivingEntity? = null
    private val slack = LobbyEase()
    private val parallaxX = LobbyEase()
    private val parallaxY = LobbyEase()
    private var pointerNowX = 0.0
    private var pointerNowY = 0.0

    @Volatile
    var tagX = 0.3f
        private set

    @Volatile
    var tagY = 0.3f
        private set

    private var cullMoved = false
    private var regraph = false

    @JvmStatic
    fun consumeCullMove(): Boolean {
        val moved = cullMoved
        cullMoved = false
        return moved
    }

    @JvmStatic
    fun consumeRegraph(): Boolean {
        val due = regraph
        regraph = false
        return due
    }

    val curtain: Float
        get() = if (!cut || phase != Phase.STAGED) 0f else 1f - Easing.smootherstep(abs(seconds / CURTAIN_SECONDS - 1f))

    @JvmStatic
    fun hidesHud(): Boolean = phase != Phase.IDLE && amount >= HAND_SHARE

    @JvmStatic
    fun animating(): Boolean = phase == Phase.RETURNING || (phase == Phase.STAGED && amount < 1f)

    @JvmStatic
    fun handSink(): Float = if (phase == Phase.IDLE) 0f else Easing.smootherstep(amount / HAND_SHARE)

    @JvmStatic
    fun bobKeep(): Float = if (phase == Phase.IDLE) 1f else 1f - amount

    @JvmStatic
    fun fovPass(world: Boolean) {
        worldPass = world
    }

    internal val arc: Float get() = side

    internal val avatarWeight: Float
        get() = if (phase == Phase.IDLE) 0f else Easing.smootherstep((amount - AVATAR_START) / AVATAR_SHARE)

    internal val pointerX: Double get() = pointerNowX
    internal val pointerY: Double get() = pointerNowY
    internal val frameDelta: Float get() = frameSeconds

    internal fun pointer(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        pointerNowX = x.toDouble().coerceIn(-1.0, 1.0)
        pointerNowY = y.toDouble().coerceIn(-1.0, 1.0)
    }

    private fun easeOut(t: Float): Float {
        val x = 1f - t.coerceIn(0f, 1f)
        return 1f - x * x * x
    }

    private fun forward(yaw: Double): PauseVec {
        val radians = Math.toRadians(yaw)
        return PauseVec(-sin(radians), 0.0, cos(radians))
    }

    private fun wrap(degrees: Float): Float {
        var angle = degrees % 360f
        if (angle >= 180f) angle -= 360f
        if (angle < -180f) angle += 360f
        return angle
    }

    private fun wrap(degrees: Double): Double {
        var angle = degrees % 360.0
        if (angle >= 180.0) angle -= 360.0
        if (angle < -180.0) angle += 360.0
        return angle
    }

    private fun lensFor(distance: Double, height: Double): Float {
        val half = height / (2.0 * SHOT_SHARE * distance)
        return (2.0 * Math.toDegrees(atan(half))).toFloat().coerceIn(MIN_LENS, MAX_LENS)
    }

    private fun openTo(entity: LivingEntity, from: PauseVec, to: PauseVec): Double {
        val length = to.sub(from).length()
        val clear = PausePlatform.clearance(entity, from, to)
        return if (clear >= length - CLEAR_EPSILON) Double.MAX_VALUE else clear - WALL_MARGIN
    }

    private fun roomy(entity: LivingEntity, chest: PauseVec, wanted: PauseVec, angle: Float, scale: Double): Boolean {
        val middle = chest.lerp(wanted, MIDDLE_SHARE)
        val across = forward((angle + RIGHT_ANGLE).toDouble()).scale(WIDTH_PROBE * scale)
        val width = PausePlatform.clearance(entity, middle, middle.add(across)) + PausePlatform.clearance(entity, middle, middle.sub(across))
        val above = PausePlatform.clearance(entity, middle, middle.add(PauseVec(0.0, HEADROOM * scale, 0.0)))
        return width >= MIN_WIDTH * scale && above >= HEADROOM * scale - CLEAR_EPSILON
    }

    private fun chooseSide(entity: LivingEntity, chest: PauseVec, yaw: Float, scale: Double): Boolean {
        var best = 0f
        var bestReach = -1.0
        for (candidate in SIDES) {
            for (sign in intArrayOf(1, -1)) {
                val angle = candidate * sign
                val wanted = chest.add(forward((yaw + angle).toDouble()).scale(REACH * scale))
                if (!roomy(entity, chest, wanted, yaw + angle, scale)) continue
                val open = openTo(entity, chest, wanted)
                if (open > bestReach + 0.2) {
                    best = angle
                    bestReach = open
                }
            }
            if (bestReach >= REACH * scale) break
        }
        if (bestReach < MIN_ROOM * scale) return false
        side = best
        reach = min(REACH * scale, bestReach)
        fullReach = REACH * scale
        return true
    }

    private fun glideTo(target: Float) {
        moveFrom = amount
        moveTo = target
        moveElapsed = 0f
        moveTime = max(GLIDE_TIME * abs(target - amount), MIN_GLIDE)
    }

    private fun advance(dt: Float) {
        if (cut) {
            amount = if (phase == Phase.STAGED && seconds >= CURTAIN_SECONDS) 1f else 0f
            return
        }
        moveElapsed += dt
        amount = moveFrom + (moveTo - moveFrom) * easeOut(moveElapsed / moveTime)
    }

    fun enter(): Boolean {
        if (phase == Phase.STAGED) return true
        val entity = PausePlatform.player() ?: return false
        if (entity !== actor) finish()
        if (phase == Phase.IDLE) {
            if (!PausePlatform.canStage(entity)) return false
            val scale = PausePlatform.height(entity) / DEFAULT_HEIGHT
            val yaw = PausePlatform.yaw(entity, 1f)
            val chest = PausePlatform.feet(entity, 1f).add(PauseVec(0.0, PausePlatform.height(entity) * CHEST_SHARE, 0.0))
            if (!chooseSide(entity, chest, yaw, scale)) return false
            savedMode = PausePlatform.cameraMode
            switched = false
            savedHud = PausePlatform.hudHidden
            actor = entity
            posed = false
            amount = 0f
            seconds = 0f
            fresh = true
            lastNanos = System.nanoTime()
            motion = PausePlatform.motionScale().coerceIn(0f, 1f)
            cut = motion < REDUCED_MOTION || Motion.reduced
            slack.snap(0.0)
            parallaxX.snap(0.0)
            parallaxY.snap(0.0)
            lens.snap(lensFor(reach, PausePlatform.height(entity)).toDouble())
            fovNow = rawFov
        } else if (!hudForced) {
            savedHud = PausePlatform.hudHidden
        }
        phase = Phase.STAGED
        glideTo(1f)
        regraph = true
        PauseAvatar.begin(entity)
        return true
    }

    private fun release() {
        if (phase != Phase.STAGED) return
        if (cut || !posed || amount <= 0f) {
            finish()
            return
        }
        phase = Phase.RETURNING
        glideTo(0f)
    }

    private fun restoreHud() {
        if (!hudForced) return
        hudForced = false
        if (PausePlatform.hudHidden) PausePlatform.hudHidden = savedHud
    }

    private fun restoreCamera() {
        if (switched && PausePlatform.thirdPerson(PausePlatform.cameraMode)) PausePlatform.cameraMode = savedMode
        switched = false
    }

    private fun finish() {
        if (phase == Phase.IDLE) return
        restoreCamera()
        restoreHud()
        phase = Phase.IDLE
        amount = 0f
        cullMoved = true
        regraph = true
        actor = null
        posed = false
        PauseAvatar.end()
    }

    @JvmStatic
    fun onScreen(screen: Screen?) {
        if (PausePlatform.player() == null) finish()
        else if (screen == null || screen is DeathScreen || unpausedBy(screen)) release()
    }

    private fun unpausedBy(screen: Screen): Boolean =
        screen !is OneClientPauseScreen && PausePlatform.pausesWorld() && !PausePlatform.gamePaused()

    @JvmStatic
    fun frame() {
        if (phase == Phase.IDLE) return
        val now = System.nanoTime()
        val dt = min((now - lastNanos) / NANOS, if (fresh) FIRST_FRAME else MAX_FRAME).coerceAtLeast(0f)
        fresh = false
        lastNanos = now
        frameSeconds = dt
        eased = false
        val player = PausePlatform.player()
        if (player == null || player !== actor) {
            finish()
            return
        }
        onScreen(ClientPlatform.currentScreen())
        when (phase) {
            Phase.STAGED -> {
                val before = amount
                seconds += dt
                advance(dt)
                if (before < 1f && amount >= 1f) regraph = true
                if (amount >= HAND_SHARE) {
                    PausePlatform.hudHidden = true
                    hudForced = true
                }
                parallaxX.step(pointerNowX * motion, PARALLAX_RATE, dt.toDouble())
                parallaxY.step(pointerNowY * motion, PARALLAX_RATE, dt.toDouble())
            }
            Phase.RETURNING -> {
                seconds += dt
                advance(dt)
                if (amount < HAND_SHARE) restoreHud()
                if (amount <= 0f) {
                    finish()
                    return
                }
            }
            Phase.IDLE -> return
        }
        PauseAvatar.step(player, dt)
    }

    private fun syncMode() {
        val mode = PausePlatform.cameraMode
        if (switched) {
            if (!PausePlatform.thirdPerson(mode)) {
                switched = false
                savedMode = mode
            }
        } else if (mode != savedMode) {
            savedMode = mode
        }
    }

    private fun showBody(camera: PauseVec, eyes: PauseVec) {
        if (!PausePlatform.firstPerson(savedMode)) return
        val distance = camera.sub(eyes).length()
        if (!switched && distance > BODY_CLEAR && phase == Phase.STAGED) {
            switched = true
            PausePlatform.showBody()
        } else if (switched && distance < BODY_HIDE && phase == Phase.RETURNING) {
            switched = false
            PausePlatform.cameraMode = savedMode
        }
    }

    private fun place(entity: LivingEntity, partial: Float, live: Pose): Pose {
        val a = amount.toDouble()
        val feet = PausePlatform.feet(entity, partial)
        val height = PausePlatform.height(entity)
        val eye = PausePlatform.eyeHeight(entity)
        val yaw = PausePlatform.yaw(entity, partial).toDouble()
        val eyes = feet.add(PauseVec(0.0, eye, 0.0))
        val chest = feet.add(PauseVec(0.0, height * CHEST_SHARE, 0.0))

        val start = PauseVec(live.x, live.y, live.z)
        val offset = start.sub(eyes)
        val startRadius = hypot(offset.x, offset.z)
        val sign = if (side >= 0f) 1.0 else -1.0
        val startAngle = if (startRadius >= ORBIT_MIN) Math.toDegrees(atan2(-offset.x, offset.z)) else yaw + sign * ARC_START
        val drift = startAngleError(eyes, start, startAngle, startRadius, offset.y)

        val glide = (1.0 - exp(-(seconds / GLIDE_SECONDS).pow(2))) * motion
        val endAngle = yaw + sign * (abs(side) - GLIDE_DEGREES * glide) + parallaxX.value * PARALLAX_YAW
        val endRadius = fullReach * (1.0 - GLIDE_PUSH * glide)
        val endRise = height * CAMERA_SHARE - eye - parallaxY.value * PARALLAX_RISE

        val angle = startAngle + wrap(endAngle - startAngle) * a
        val radius = startRadius + (endRadius - startRadius) * a
        val rise = offset.y + (endRise - offset.y) * a
        val wanted = eyes.add(forward(angle).scale(radius)).add(PauseVec(0.0, rise, 0.0)).add(drift.scale(1.0 - a))

        val toWanted = wanted.sub(eyes)
        val length = toWanted.length()
        val clear = if (length > MIN_LENGTH) PausePlatform.clearance(entity, eyes, wanted) else length
        val blocked = clear < length - CLEAR_EPSILON
        val short = if (blocked) max(0.0, length - (clear - WALL_MARGIN * a)) else 0.0
        if (!eased) {
            if (short > slack.value) slack.snap(short) else slack.step(short, ROOM_RATE, frameSeconds.toDouble())
        } else if (short > slack.value) {
            slack.snap(short)
        }
        val allowed = (length - slack.value).coerceIn(min(length, MIN_LENGTH), length)
        val camera = if (length > MIN_LENGTH) eyes.add(toWanted.scale(allowed / length)) else wanted

        val shot = if (slack.value > 0.0) allowed.coerceIn(MIN_ROOM, fullReach) else fullReach
        if (!eased) lens.step(lensFor(shot, height).toDouble(), LENS_RATE, frameSeconds.toDouble())
        eased = true
        fovNow = rawFov + (lens.value.toFloat() - rawFov) * amount

        val aim = eyes.lerp(chest, Easing.smootherstep((a / AIM_SHARE).toFloat()).toDouble()).sub(camera)
        val aimed = aim.length() > MIN_LENGTH
        val aimYaw = if (aimed) Math.toDegrees(atan2(-aim.x, aim.z)).toFloat() else live.yaw
        val aimPitch = if (aimed) (-Math.toDegrees(atan2(aim.y, hypot(aim.x, aim.z)))).toFloat() else live.pitch
        val shift = Math.toDegrees(atan(-SCREEN_X * PausePlatform.aspect() * tan(Math.toRadians(fovNow / 2.0)))).toFloat()
        val turn = amount * (1f + TURN_LEAD) / (1f + TURN_LEAD * amount)
        val outYaw = live.yaw + wrap(aimYaw + shift * amount - live.yaw) * turn
        val outPitch = live.pitch + (aimPitch - live.pitch) * turn

        showBody(camera, eyes)
        project(camera, outYaw, outPitch, feet.add(PauseVec(0.0, height + TAG_LIFT, 0.0)))
        return Pose(camera.x, camera.y, camera.z, outYaw, outPitch)
    }

    private fun startAngleError(eyes: PauseVec, start: PauseVec, angle: Double, radius: Double, rise: Double): PauseVec =
        start.sub(eyes.add(forward(angle).scale(radius)).add(PauseVec(0.0, rise, 0.0)))

    private fun view(yaw: Float, pitch: Float): PauseVec {
        val y = Math.toRadians(yaw.toDouble())
        val x = Math.toRadians(pitch.toDouble())
        return PauseVec(-sin(y) * cos(x), -sin(x), cos(y) * cos(x))
    }

    private fun project(camera: PauseVec, yaw: Float, pitch: Float, point: PauseVec) {
        val look = view(yaw, pitch)
        val right = view(yaw + RIGHT_ANGLE, 0f)
        val up = view(yaw, pitch - RIGHT_ANGLE)
        val delta = point.sub(camera)
        val depth = delta.dot(look)
        if (depth < MIN_LENGTH) return
        val half = tan(Math.toRadians(fovNow / 2.0))
        tagX = (0.5 + 0.5 * (delta.dot(right) / depth) / (half * PausePlatform.aspect())).toFloat()
        tagY = (0.5 - 0.5 * (delta.dot(up) / depth) / half).toFloat()
    }

    private fun publish(result: Pose): Pose {
        posed = true
        cullMoved = true
        return result
    }

    @JvmStatic
    fun pose(entity: LivingEntity?, partial: Float, x: Double, y: Double, z: Double, yaw: Float, pitch: Float, eye: Float): Pose? {
        if (phase == Phase.IDLE || entity == null || entity !== actor) return null
        syncMode()
        val live = if (switched) {
            val feet = PausePlatform.feet(entity, partial)
            Pose(feet.x, feet.y + eye, feet.z, PausePlatform.yaw(entity, partial), PausePlatform.pitch(entity, partial))
        } else {
            Pose(x, y, z, yaw, pitch)
        }
        return publish(place(entity, partial, live))
    }

    @JvmStatic
    fun fov(original: Float): Float {
        if (!worldPass) return original
        rawFov = original
        return if (phase == Phase.IDLE || fovNow <= 0f) original else fovNow
    }

    //? if = 1.8.9 {
    /*@JvmStatic
    fun applyLegacyCamera(tickDelta: Float): Boolean {
        if (phase == Phase.IDLE) return false
        val entity = PausePlatform.player() ?: return false
        if (entity !== actor) return false
        syncMode()
        val feet = PausePlatform.feet(entity, tickDelta)
        val eye = feet.add(PauseVec(0.0, PausePlatform.eyeHeight(entity), 0.0))
        val yaw = PausePlatform.yaw(entity, tickDelta)
        val pitch = PausePlatform.pitch(entity, tickDelta)
        val mode = if (switched) FIRST_PERSON_MODE else savedMode
        val live = when {
            PausePlatform.firstPerson(mode) -> eye.sub(view(yaw, pitch).scale(LEGACY_EYE_BACK)).let { Pose(it.x, it.y, it.z, yaw, pitch) }
            else -> {
                val front = !PausePlatform.thirdPerson(mode)
                val rawYaw = PausePlatform.yaw(entity, 1f)
                val rawPitch = PausePlatform.pitch(entity, 1f)
                val ray = view(rawYaw, if (front) rawPitch + HALF_TURN else rawPitch).scale(LEGACY_THIRD_PERSON)
                val distance = PausePlatform.legacyZoom(eye, ray, LEGACY_THIRD_PERSON)
                val spot = eye.sub(ray.scale(distance / LEGACY_THIRD_PERSON))
                if (front) Pose(spot.x, spot.y, spot.z, yaw + HALF_TURN, -pitch) else Pose(spot.x, spot.y, spot.z, yaw, pitch)
            }
        }
        val done = publish(place(entity, tickDelta, live))
        GlStateManager.rotatef(done.pitch, 1f, 0f, 0f)
        GlStateManager.rotatef(done.yaw + HALF_TURN, 0f, 1f, 0f)
        GlStateManager.translatef((feet.x - done.x).toFloat(), (feet.y - done.y).toFloat(), (feet.z - done.z).toFloat())
        return true
    }

    private const val LEGACY_THIRD_PERSON = 4.0
    private const val LEGACY_EYE_BACK = 0.1
    private const val FIRST_PERSON_MODE = 0
    private const val HALF_TURN = 180f
    *///?}

    private const val NANOS = 1_000_000_000f
    private const val DEFAULT_HEIGHT = 1.8
    private const val MIN_LENGTH = 0.05
    private const val RIGHT_ANGLE = 90f
}
