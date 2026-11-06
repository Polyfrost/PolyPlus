package org.polyfrost.polyplus.client.gui.pausescreen

import net.minecraft.client.gui.screens.Screen
import org.polyfrost.polyplus.client.lobby.LobbyEase
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.abs
import kotlin.math.acos
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
    private const val PULL_SECONDS = 2.0f
    private const val RETURN_SECONDS = 0.12f
    private const val HAND_SECONDS = 0.06f
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
    private const val AIM_SHARE = 0.5
    private const val BODY_RETURN = 0.4
    private const val FROM_SHARE = 0.4
    private const val TURN_LEAD = 1.37f
    private const val BODY_CLEAR = 0.35
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
    private var returnSeconds = 0f
    private var lastNanos = 0L
    private var frameSeconds = 0f
    private var side = SIDES[0]
    private var reach = REACH
    private var fullReach = REACH
    private var startFov = 0f
    private var liveFov = 70f
    private val lens = LobbyEase(60.0)
    private var fovNow = 0f
    private var motion = 1f
    private var anchor: Pose? = null
    private var last: Pose? = null
    private var returnFrom: Pose? = null
    private var returnFov = 0f
    private var savedMode = 0
    private var savedHud = false
    private var bodyShown = false
    private var actor: LivingEntity? = null
    private val room = LobbyEase()
    private val parallaxX = LobbyEase()
    private val parallaxY = LobbyEase()
    private var pointerNowX = 0.0
    private var pointerNowY = 0.0
    private var cameraNow = PauseVec(0.0, 0.0, 0.0)

    @Volatile
    var tagX = 0.3f
        private set

    @Volatile
    var tagY = 0.3f
        private set

    @JvmStatic
    fun staged(): Boolean = phase == Phase.STAGED && bodyShown

    val curtain: Float
        get() = if (!cut || phase != Phase.STAGED) 0f else 1f - smooth(abs(seconds / CURTAIN_SECONDS - 1f))

    @JvmStatic
    fun hidesHud(): Boolean = phase == Phase.STAGED && seconds >= HAND_SECONDS

    @JvmStatic
    fun handSink(): Float = when (phase) {
        Phase.IDLE -> 0f
        Phase.STAGED -> smooth(seconds / HAND_SECONDS)
        Phase.RETURNING -> 1f - easeOut(returnSeconds / RETURN_SECONDS)
    }

    private val progress: Float
        get() = if (cut && seconds >= CURTAIN_SECONDS) 1f else curve(seconds / PULL_SECONDS)

    internal val camera: PauseVec get() = cameraNow

    internal val avatarWeight: Float
        get() = when (phase) {
            Phase.IDLE -> 0f
            Phase.STAGED -> smooth((progress - AVATAR_START) / AVATAR_SHARE)
            Phase.RETURNING -> 1f - easeOut(returnSeconds / RETURN_SECONDS)
        }

    internal val pointerX: Double get() = pointerNowX
    internal val pointerY: Double get() = pointerNowY
    internal val frameDelta: Float get() = frameSeconds

    internal fun pointer(x: Float, y: Float) {
        pointerNowX = x.toDouble()
        pointerNowY = y.toDouble()
    }

    private fun curve(t: Float): Float {
        val left = 1f - t.coerceIn(0f, 1f)
        return 1f - left * left * left * left
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * x * (x * (x * 6f - 15f) + 10f)
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
            savedHud = PausePlatform.hudHidden
            bodyShown = !PausePlatform.firstPerson(savedMode)
            actor = entity
            anchor = null
            last = null
            room.snap(fullReach)
            startFov = liveFov
            fovNow = liveFov
        } else {
            anchor = last
            startFov = fovNow
        }
        motion = PausePlatform.motionScale().coerceIn(0f, 1f)
        cut = motion < REDUCED_MOTION
        phase = Phase.STAGED
        seconds = 0f
        returnFrom = null
        parallaxX.snap(0.0)
        parallaxY.snap(0.0)
        lens.snap(lensFor(reach, PausePlatform.height(entity)).toDouble())
        lastNanos = System.nanoTime()
        PauseAvatar.begin(entity)
        return true
    }

    private fun release() {
        if (phase != Phase.STAGED) return
        val from = last
        PausePlatform.hudHidden = savedHud
        if (from == null) {
            finish()
            return
        }
        returnFrom = from
        returnFov = fovNow
        returnSeconds = 0f
        phase = Phase.RETURNING
    }

    private fun finish() {
        if (phase == Phase.IDLE) return
        if (bodyShown && PausePlatform.firstPerson(savedMode)) PausePlatform.cameraMode = savedMode
        PausePlatform.hudHidden = savedHud
        phase = Phase.IDLE
        actor = null
        anchor = null
        last = null
        returnFrom = null
        PauseAvatar.end()
    }

    @JvmStatic
    fun onScreen(screen: Screen?) {
        if (PausePlatform.player() == null) finish()
        else if (screen == null || screen is DeathScreen) release()
    }

    @JvmStatic
    fun frame() {
        if (phase == Phase.IDLE) return
        val now = System.nanoTime()
        val dt = min((now - lastNanos) / NANOS, if (phase == Phase.STAGED && seconds == 0f) FIRST_FRAME else MAX_FRAME)
        lastNanos = now
        frameSeconds = dt
        val player = PausePlatform.player()
        if (player == null || player !== actor) {
            finish()
            return
        }
        onScreen(ClientPlatform.currentScreen())
        when (phase) {
            Phase.STAGED -> {
                seconds += dt
                if (seconds >= HAND_SECONDS) PausePlatform.hudHidden = true
                parallaxX.step(pointerNowX * motion, PARALLAX_RATE, dt.toDouble())
                parallaxY.step(pointerNowY * motion, PARALLAX_RATE, dt.toDouble())
            }
            Phase.RETURNING -> {
                returnSeconds += dt
                if (returnSeconds >= RETURN_SECONDS) {
                    finish()
                    return
                }
            }
            Phase.IDLE -> return
        }
        PauseAvatar.step(player, dt)
    }

    private fun stagePose(entity: LivingEntity, partial: Float, start: Pose): Pose {
        val p = progress.toDouble()
        val feet = PausePlatform.feet(entity, partial)
        val height = PausePlatform.height(entity)
        val eye = PausePlatform.eyeHeight(entity)
        val yaw = PausePlatform.yaw(entity, partial)
        val chest = feet.add(PauseVec(0.0, height * CHEST_SHARE, 0.0))
        val eyes = feet.add(PauseVec(0.0, eye, 0.0))

        val glide = (1.0 - exp(-(seconds / GLIDE_SECONDS).pow(2))) * motion
        val sign = if (side >= 0f) 1.0 else -1.0
        val arc = sign * (ARC_START + (abs(side) - ARC_START) * p - GLIDE_DEGREES * glide) + parallaxX.value * PARALLAX_YAW
        val radius = fullReach * p * (1.0 - GLIDE_PUSH * glide)
        val rise = eye + (height * CAMERA_SHARE - eye) * p - parallaxY.value * PARALLAX_RISE
        val wanted = feet.add(forward(yaw + arc).scale(radius)).add(PauseVec(0.0, rise, 0.0))

        val toWanted = wanted.sub(eyes)
        val length = toWanted.length()
        val open = min(if (length > MIN_LENGTH) openTo(entity, eyes, wanted) else fullReach, fullReach)
        if (open < room.value) room.snap(open) else room.step(open, ROOM_RATE, frameSeconds.toDouble())
        val allowed = min(length, max(room.value, MIN_LENGTH))
        val path = if (length > MIN_LENGTH) eyes.add(toWanted.scale(allowed / length)) else wanted

        lens.step(lensFor(room.value.coerceIn(MIN_ROOM, fullReach), height).toDouble(), LENS_RATE, frameSeconds.toDouble())

        val startVec = PauseVec(start.x, start.y, start.z)
        val fromWeight = 1.0 - smooth((p / FROM_SHARE).toFloat())
        val camera = path.add(startVec.sub(eyes).scale(fromWeight.toDouble()))

        fovNow = startFov + (lens.value.toFloat() - startFov) * p.toFloat()
        val aim = eyes.lerp(chest, smooth((p / AIM_SHARE).toFloat()).toDouble()).sub(camera)
        val aimYaw = Math.toDegrees(atan2(-aim.x, aim.z)).toFloat()
        val aimPitch = (-Math.toDegrees(atan2(aim.y, hypot(aim.x, aim.z)))).toFloat()
        val offset = Math.toDegrees(atan(-SCREEN_X * PausePlatform.aspect() * tan(Math.toRadians(fovNow / 2.0)))).toFloat()
        val turn = p.toFloat() * (1f + TURN_LEAD) / (1f + TURN_LEAD * p.toFloat())
        val outYaw = start.yaw + wrap(aimYaw + offset * p.toFloat() - start.yaw) * turn
        val outPitch = start.pitch + (aimPitch - start.pitch) * turn

        if (!bodyShown && seconds >= HAND_SECONDS && eyes.sub(camera).length() > BODY_CLEAR) {
            bodyShown = true
            PausePlatform.showBody()
        }
        project(camera, outYaw, outPitch, feet.add(PauseVec(0.0, height + TAG_LIFT, 0.0)))
        return Pose(camera.x, camera.y, camera.z, outYaw, outPitch)
    }

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

    private fun returnPose(entity: LivingEntity, partial: Float, live: Pose): Pose? {
        val from = returnFrom ?: return null
        val weight = easeOut(returnSeconds / RETURN_SECONDS)
        val target = if (PausePlatform.firstPerson(savedMode)) {
            PausePlatform.feet(entity, partial).add(PauseVec(0.0, PausePlatform.eyeHeight(entity), 0.0))
        } else {
            PauseVec(live.x, live.y, live.z)
        }
        val camera = PauseVec(from.x, from.y, from.z).lerp(target, weight.toDouble())
        fovNow = returnFov + (liveFov - returnFov) * weight
        if (bodyShown && PausePlatform.firstPerson(savedMode) && camera.sub(target).length() < BODY_RETURN) {
            bodyShown = false
            PausePlatform.cameraMode = savedMode
        }
        return Pose(camera.x, camera.y, camera.z, from.yaw + wrap(live.yaw - from.yaw) * weight, from.pitch + (live.pitch - from.pitch) * weight)
    }

    @JvmStatic
    fun pose(entity: LivingEntity?, partial: Float, x: Double, y: Double, z: Double, yaw: Float, pitch: Float): Pose? {
        if (phase == Phase.IDLE || entity == null || entity !== actor) return null
        val live = Pose(x, y, z, yaw, pitch)
        val result = if (phase == Phase.STAGED) {
            val start = anchor ?: live.also { anchor = it }
            stagePose(entity, partial, start)
        } else {
            returnPose(entity, partial, live)
        } ?: return null
        last = result
        cameraNow = PauseVec(result.x, result.y, result.z)
        return result
    }

    @JvmStatic
    fun fov(original: Float): Float {
        if (phase == Phase.IDLE) liveFov = original
        return if (phase == Phase.IDLE || fovNow <= 0f) original else fovNow
    }

    //? if = 1.8.9 {
    /*@JvmStatic
    fun applyLegacyCamera(tickDelta: Float): Boolean {
        if (phase == Phase.IDLE) return false
        val entity = PausePlatform.player() ?: return false
        val feet = PausePlatform.feet(entity, tickDelta)
        val eye = feet.add(PauseVec(0.0, PausePlatform.eyeHeight(entity), 0.0))
        val yaw = PausePlatform.yaw(entity, tickDelta)
        val pitch = PausePlatform.pitch(entity, tickDelta)
        val back = if (PausePlatform.firstPerson(savedMode)) 0.0 else LEGACY_THIRD_PERSON
        val live = eye.sub(view(yaw, pitch).scale(back))
        val done = pose(entity, tickDelta, live.x, live.y, live.z, yaw, pitch) ?: return false
        GlStateManager.rotatef(done.pitch, 1f, 0f, 0f)
        GlStateManager.rotatef(done.yaw + 180f, 0f, 1f, 0f)
        GlStateManager.translatef((feet.x - done.x).toFloat(), (feet.y - done.y).toFloat(), (feet.z - done.z).toFloat())
        return true
    }

    private const val LEGACY_THIRD_PERSON = 4.0
    *///?}

    private const val NANOS = 1_000_000_000f
    private const val DEFAULT_HEIGHT = 1.8
    private const val MIN_LENGTH = 0.05
    private const val RIGHT_ANGLE = 90f
}
