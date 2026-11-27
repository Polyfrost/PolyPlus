@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.emotes.Emote
import org.polyfrost.polyplus.client.emotes.EmoteBundles
import org.polyfrost.polyplus.client.emotes.playback.EmoteController
import org.polyfrost.polyplus.client.utils.Easing
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

object LobbyGrab {
    private const val NANOS = 1_000_000_000.0
    private const val TICK_SECONDS = 0.05
    private const val NECK = 1.4
    private const val ROD = 0.85
    private const val GRAVITY = 30.0
    private const val DAMPING = 0.8
    private const val SUBSTEPS = 4
    private const val MAX_SWING = 70.0
    private const val FOLLOW_RATE = 16.0
    private const val MAX_REACH = 10.0
    private const val MIN_LIFT = 0.45
    private const val MAX_LIFT = 3.5
    private const val MIN_SCALE = 1.0e-4
    private const val DROP_GRAVITY = 32.0
    private const val MIN_DROP_TICKS = 5
    private const val HOP = 0.35
    private const val SQUASH_TICKS = 6
    private const val DAZE_TICKS = 14
    private const val HOP_PACE = 0.3
    private const val NEAR_HOME = 1.5
    private const val HEAD_HEIGHT = 1.6
    private const val TURN_AROUND = 180f
    private const val BASE_TICKS = 10.0
    private const val FLAIL = 1.0
    private const val ARM_SWING = 30.0
    private const val ARM_SPREAD = 18.0
    private const val LEG_KICK = 32.0
    private const val HOP_ARC = 4.0

    private val DANGLE_ID = Identifier.fromNamespaceAndPath("polyplus", "lobby/dangle")

    private var seen: LobbyAvatar? = null
    private var visible = false
    private var centerX = 0.0
    private var halfWidth = 0.0
    private var bottom = 0.0
    private var top = 0.0
    private var neckX = 0.0
    private var neckY = 0.0
    private var perRight = 0.0
    private var perUp = 0.0
    private var right = Vec3(1.0, 0.0, 0.0)
    private var eye = Vec3(0.0, 0.0, 0.0)
    private var cameraYaw = 0f

    private var held: Hold? = null
    private var drop: Drop? = null
    private var strolling: LobbyAvatar? = null
    private var level: ClientLevel? = null

    private val dangling by lazy { emote(FLAIL) }
    private val still by lazy { emote(0.0) }

    private class Hold(val avatar: LobbyAvatar, val anchor: Vec3, val previousClock: (() -> Long)?) {
        var neckX = 0.0
        var neckY = 0.0
        var perRight = 1.0
        var perUp = 1.0
        var u = 0.0
        var v = 0.0
        var targetU = 0.0
        var targetV = MIN_LIFT
        var bobU = 0.0
        var bobV = -ROD
        var lastBobU = 0.0
        var lastBobV = -ROD
        var swing = 0.0
        var lastSwing = 0.0
        var tickNanos = System.nanoTime()
        var starting = false
    }

    private class Drop(val avatar: LobbyAvatar, val from: Vec3, val to: Vec3, val ticks: Int, val hop: Double, val home: Boolean) {
        var elapsed = 0
        var after = -1
    }

    private fun ready(): Boolean = LobbyScene.showing() && LobbyScene.revealStarted() && !LobbyScene.introPlaying()

    fun sight(avatar: LobbyAvatar, feetX: Double, feetY: Double, headX: Double, headY: Double, sideX: Double, camera: Vec3, yaw: Float) {
        seen = avatar
        visible = true
        val middleX = (feetX + headX) / 2.0
        centerX = middleX
        halfWidth = abs(sideX - middleX)
        bottom = minOf(feetY, headY)
        top = maxOf(feetY, headY)
        eye = camera
        if (held != null) return
        val share = NECK / avatar.bbHeight
        neckX = feetX + (headX - feetX) * share
        neckY = feetY + (headY - feetY) * share
        perUp = (headY - feetY) / avatar.bbHeight
        perRight = (sideX - middleX) / LobbyAntics.HALF_WIDTH
        cameraYaw = yaw
        val yawRad = Math.toRadians(yaw.toDouble())
        right = Vec3(-cos(yawRad), 0.0, -sin(yawRad))
    }

    fun lose() {
        visible = false
    }

    fun touches(x: Double, y: Double): Boolean {
        if (!visible || seen == null || !ready()) return false
        val ndcX = x * 2.0 - 1.0
        val ndcY = 1.0 - y * 2.0
        return abs(ndcX - centerX) <= halfWidth && ndcY >= bottom && ndcY <= top
    }

    fun grab(x: Double, y: Double): Boolean {
        val avatar = seen ?: return false
        if (held != null || !visible || !ready() || !usable(avatar)) return false
        if (abs(perRight) < MIN_SCALE || abs(perUp) < MIN_SCALE) return false
        if (avatar.arrival != LobbyAvatar.Arrival.LANDED) avatar.halt()
        strolling = null
        val controller = controller(avatar)
        val hold = Hold(avatar, avatar.position(), controller.clock)
        hold.neckX = neckX
        hold.neckY = neckY
        hold.perRight = perRight
        hold.perUp = perUp
        drop = null
        held = hold
        aim(hold, x, y)
        controller.clock = { clockMs(hold) }
        play(hold)
        return true
    }

    fun drag(x: Double, y: Double) {
        held?.let { aim(it, x, y) }
    }

    fun release() {
        val hold = held ?: return
        held = null
        val avatar = hold.avatar
        val controller = controller(avatar)
        controller.clock = hold.previousClock
        if (Motion.reduced) controller.stop() else LobbyEntrance.fallPose()?.let(controller::play) ?: controller.stop()
        val from = avatar.position()
        val rest = avatar.rest.pos
        val away = (from.x - rest.x) * (from.x - rest.x) + (from.z - rest.z) * (from.z - rest.z) > NEAR_HOME * NEAR_HOME
        val ground = if (away) level?.let { LobbyEntrance.landing(it, from) } else null
        drop = if (ground == null) dropping(avatar, from, rest, false) else dropping(avatar, from, Vec3(from.x, ground, from.z), true)
    }

    fun homeward(avatar: LobbyAvatar): Boolean = strolling === avatar

    fun handling(avatar: LobbyAvatar): Boolean = held?.avatar === avatar || drop?.avatar === avatar || strolling === avatar

    fun tick(level: ClientLevel, avatar: LobbyAvatar?) {
        this.level = level
        strolling?.let {
            if (it !== avatar || it.arrival != LobbyAvatar.Arrival.ARRIVING) {
                strolling = null
            } else if (LobbyWardrobe.open) {
                strolling = null
                it.arriveNow()
            }
        }
        val hold = held
        if (hold != null) {
            if (avatar === hold.avatar && usable(avatar)) step(hold) else cancel()
            return
        }
        val falling = drop ?: return
        if (avatar === falling.avatar && usable(avatar)) fall(falling) else cancel()
    }

    fun cancel() {
        val hold = held
        val falling = drop
        held = null
        drop = null
        val avatar = hold?.avatar ?: falling?.avatar ?: return
        val controller = controller(avatar)
        if (hold != null) controller.clock = hold.previousClock
        if (ours(controller.playing)) controller.stop()
        avatar.pose = Pose.STANDING
        if (avatar.arrival != LobbyAvatar.Arrival.LANDED) return
        val rest = avatar.rest
        avatar.place(rest.pos, rest.yaw)
        avatar.face(rest.yaw, 0f)
        avatar.setOldPosAndRot()
    }

    fun steer(avatar: LobbyAvatar): Boolean {
        val active = held?.avatar === avatar || drop?.avatar === avatar
        if (!active) return false
        val yaw = cameraYaw + TURN_AROUND
        val head = avatar.position().add(0.0, HEAD_HEIGHT, 0.0)
        val flat = sqrt((eye.x - head.x) * (eye.x - head.x) + (eye.z - head.z) * (eye.z - head.z))
        val pitch = -Math.toDegrees(atan2(eye.y - head.y, flat)).toFloat()
        avatar.gaze.aim(yaw, pitch, yaw, true)
        return true
    }

    fun reset() {
        cancel()
        strolling = null
        level = null
        seen = null
        visible = false
    }

    private fun usable(avatar: LobbyAvatar): Boolean =
        avatar.isLocal && (avatar.arrival == LobbyAvatar.Arrival.LANDED || strolling === avatar) && !avatar.dismissed && !avatar.spectral && !LobbyWardrobe.open && LobbyScene.showing()

    private fun controller(avatar: LobbyAvatar): EmoteController = (avatar as PlayerEmotesAccess).`polyplus$emoteController`()

    private fun aim(hold: Hold, x: Double, y: Double) {
        val ndcX = x * 2.0 - 1.0
        val ndcY = 1.0 - y * 2.0
        val rest = hold.avatar.rest.pos
        val shift = (hold.anchor.x - rest.x) * right.x + (hold.anchor.z - rest.z) * right.z
        hold.targetU = ((ndcX - hold.neckX) / hold.perRight).coerceIn(-MAX_REACH - shift, MAX_REACH - shift)
        hold.targetV = ((ndcY - hold.neckY) / hold.perUp).coerceIn(MIN_LIFT, MAX_LIFT)
    }

    private fun step(hold: Hold) {
        val reduced = Motion.reduced
        val fromU = hold.u
        val fromV = hold.v
        val follow = if (reduced) 1.0 else 1.0 - exp(-FOLLOW_RATE * TICK_SECONDS)
        hold.u += (hold.targetU - hold.u) * follow
        hold.v += (hold.targetV - hold.v) * follow
        hold.lastSwing = hold.swing
        hold.swing = if (reduced) 0.0 else swing(hold, fromU, fromV)
        if (reduced) hold.lastSwing = 0.0
        hold.tickNanos = System.nanoTime()
        val avatar = hold.avatar
        val head = avatar.yHeadRot
        avatar.place(within(avatar.rest.pos, hold.anchor.add(right.x * hold.u, hold.v, right.z * hold.u)), avatar.yBodyRot)
        avatar.yHeadRot = head
        avatar.pose = Pose.STANDING
        if (controller(avatar).playing !== pose()) play(hold)
    }

    private fun within(rest: Vec3, at: Vec3): Vec3 {
        val dx = at.x - rest.x
        val dz = at.z - rest.z
        val reach = sqrt(dx * dx + dz * dz)
        if (reach <= MAX_REACH) return at
        val share = MAX_REACH / reach
        return Vec3(rest.x + dx * share, at.y, rest.z + dz * share)
    }

    private fun swing(hold: Hold, fromU: Double, fromV: Double): Double {
        val dt = TICK_SECONDS / SUBSTEPS
        val keep = 1.0 - DAMPING * dt
        val limit = Math.toRadians(MAX_SWING)
        for (i in 1..SUBSTEPS) {
            val share = i.toDouble() / SUBSTEPS
            val pivotU = Mth.lerp(share, fromU, hold.u)
            val pivotV = Mth.lerp(share, fromV, hold.v)
            val nextU = hold.bobU + (hold.bobU - hold.lastBobU) * keep
            val nextV = hold.bobV + (hold.bobV - hold.lastBobV) * keep - GRAVITY * dt * dt
            hold.lastBobU = hold.bobU
            hold.lastBobV = hold.bobV
            val angle = atan2(nextU - pivotU, pivotV - nextV).coerceIn(-limit, limit)
            hold.bobU = pivotU + sin(angle) * ROD
            hold.bobV = pivotV - cos(angle) * ROD
        }
        return Math.toDegrees(atan2(hold.bobU - hold.u, hold.v - hold.bobV))
    }

    private fun dropping(avatar: LobbyAvatar, from: Vec3, to: Vec3, home: Boolean): Drop {
        val height = (from.y - to.y).coerceAtLeast(0.0)
        val falling = ceil(sqrt(2.0 * height / DROP_GRAVITY) / TICK_SECONDS).toInt()
        val hopping = ceil(sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z)) / HOP_PACE).toInt()
        return Drop(avatar, from, to, maxOf(MIN_DROP_TICKS, falling, hopping), (HOP - height).coerceAtLeast(0.0), home)
    }

    private fun fall(falling: Drop) {
        val avatar = falling.avatar
        if (falling.after >= 0) {
            if (++falling.after == SQUASH_TICKS) avatar.pose = Pose.STANDING
            if (falling.after < if (falling.home) SQUASH_TICKS + DAZE_TICKS else SQUASH_TICKS) return
            drop = null
            if (falling.home) walkHome(avatar)
            return
        }
        falling.elapsed++
        val progress = (falling.elapsed.toDouble() / falling.ticks).coerceAtMost(1.0)
        val rest = falling.to
        val slide = Easing.smoothstep(progress)
        val y = Mth.lerp(progress * progress, falling.from.y, rest.y) + falling.hop * HOP_ARC * progress * (1.0 - progress)
        val head = avatar.yHeadRot
        avatar.place(Vec3(Mth.lerp(slide, falling.from.x, rest.x), y, Mth.lerp(slide, falling.from.z, rest.z)), avatar.yBodyRot)
        avatar.yHeadRot = head
        if (progress < 1.0) return
        controller(avatar).stop()
        avatar.pose = Pose.CROUCHING
        falling.after = 0
        if (falling.home) level?.let { LobbyEntrance.land(it, rest, falling.from.y - rest.y) }
    }

    private fun walkHome(avatar: LobbyAvatar) {
        if (LobbyScene.strollHome(avatar)) {
            strolling = avatar
            return
        }
        drop = dropping(avatar, avatar.position(), avatar.rest.pos, false)
    }

    private fun play(hold: Hold) {
        val controller = controller(hold.avatar)
        hold.starting = true
        pose()?.let(controller::play)
        hold.starting = false
    }

    private fun pose(): Emote? = if (Motion.reduced) still else dangling

    private fun ours(emote: Emote?): Boolean = emote != null && (emote.id == DANGLE_ID || emote === LobbyEntrance.fallPose())

    private fun clockMs(hold: Hold): Long {
        if (hold.starting) return 0L
        val partial = ((System.nanoTime() - hold.tickNanos) / NANOS / TICK_SECONDS).coerceIn(0.0, 1.0)
        val roll = -Mth.lerp(partial, hold.lastSwing, hold.swing).coerceIn(-MAX_SWING, MAX_SWING)
        return ((BASE_TICKS + MAX_SWING + roll) * BedrockConstants.MS_PER_TICK).toLong()
    }

    private fun emote(flail: Double): Emote? {
        val start = BASE_TICKS / BedrockConstants.TICKS_PER_SECOND
        val end = (BASE_TICKS + 2.0 * MAX_SWING) / BedrockConstants.TICKS_PER_SECOND
        val arm = ARM_SWING * flail
        val spread = ARM_SPREAD * flail
        val kick = LEG_KICK * flail
        val json = """
            {
              "format_version": "1.8.0",
              "animations": {
                "animation.lobby.dangle": {
                  "loop": "hold_on_last_frame",
                  "animation_length": $end,
                  "bones": {
                    "root": {"rotation": {"$start": [0, 0, ${-MAX_SWING}], "$end": [0, 0, $MAX_SWING]}},
                    "rightArm": {"rotation": ["-25 + math.sin(q.life_time * 830) * $arm", 0, "60 + math.sin(q.life_time * 610) * $spread"]},
                    "leftArm": {"rotation": ["-25 - math.sin(q.life_time * 830) * $arm", 0, "-60 - math.sin(q.life_time * 610) * $spread"]},
                    "rightLeg": {"rotation": ["math.sin(q.life_time * 1000) * $kick", 0, 5]},
                    "leftLeg": {"rotation": ["-math.sin(q.life_time * 1000) * $kick", 0, -5]}
                  }
                }
              }
            }
        """.trimIndent()
        return EmoteBundles.fromAnimations(json) { DANGLE_ID }["animation.lobby.dangle"]
    }
}
