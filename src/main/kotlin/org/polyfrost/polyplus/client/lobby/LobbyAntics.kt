@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.lobby

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Pose
//? if = 1.8.9
/*import net.minecraft.resource.Identifier*/
import net.minecraft.world.phys.Vec3
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.utils.Easing
//? if > 1.8.9 {
import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Quaternionf
import org.polyfrost.polyplus.client.utils.rotateBy
//?} else {
/*import net.minecraft.client.render.platform.GlStateManager
*///?}
import kotlin.math.PI
import kotlin.math.abs

object LobbyAntics {
    private val CODE = intArrayOf(
        InputConstants.KEY_UP, InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_DOWN,
        InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT,
        InputConstants.KEY_B, InputConstants.KEY_A,
    )
    private val GOLDEN_CLOCKS = longArrayOf(12800L, 23200L)

    private const val NANOS = 1_000_000_000.0
    private const val DAY_TICKS = 24000L
    private const val HALF_DAY = 12000L
    private const val SPIN_SECONDS = 1.0
    private const val GOLDEN_SECONDS = 6.0
    private const val SWEEP_SECONDS = 1.4
    private const val COOLDOWN_SECONDS = 10.0
    private const val CHIME_PITCH = 1.6f
    private const val CHIME_VOLUME = 0.2f
    private const val CLICK_GAP_SECONDS = 0.5
    private const val WAVE_CLICKS = 3
    private const val CROUCH_CLICKS = 8
    private const val CROUCH_STEP_TICKS = 3
    private const val CROUCH_TICKS = CROUCH_STEP_TICKS * 8
    const val HALF_WIDTH = 0.4

    private var typed = 0
    private var triggeredAt = 0L
    private var goldenAt = 0L
    private var spinAt = 0L

    private var avatarId = -1
    private var centerX = 0f
    private var halfWidth = 0f
    private var bottom = 0f
    private var top = 0f

    private var lastClick = 0L
    private var streak = 0
    private var waveQueued = false
    private var crouchTicks = 0

    private fun since(start: Long, now: Long = System.nanoTime()): Double = (now - start) / NANOS

    private fun ready(): Boolean = LobbyScene.showing() && LobbyScene.revealStarted() && !LobbyScene.introPlaying()

    fun key(key: Int, calm: Boolean) {
        if (!calm || !ready()) {
            typed = 0
            return
        }
        typed = when {
            key == CODE[typed] -> typed + 1
            key != CODE[0] -> 0
            typed == 2 -> 2
            else -> 1
        }
        if (typed < CODE.size) return
        typed = 0
        val now = System.nanoTime()
        if (triggeredAt != 0L && since(triggeredAt, now) < COOLDOWN_SECONDS) return
        triggeredAt = now
        goldenAt = now
        spinAt = if (Motion.reduced) 0L else now
        chime()
    }

    private fun chime() {
        //? if > 1.8.9 {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, CHIME_PITCH, CHIME_VOLUME))
        //?} else {
        /*Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(Identifier("random.levelup"), CHIME_PITCH, CHIME_VOLUME))
        *///?}
    }

    fun clock(real: Long): Long {
        if (goldenAt == 0L) return real
        val elapsed = since(goldenAt)
        if (elapsed >= GOLDEN_SECONDS) {
            goldenAt = 0L
            return real
        }
        val weight = when {
            Motion.reduced -> 1.0
            elapsed < SWEEP_SECONDS -> Easing.smoothstep(elapsed / SWEEP_SECONDS)
            elapsed > GOLDEN_SECONDS - SWEEP_SECONDS -> Easing.smoothstep((GOLDEN_SECONDS - elapsed) / SWEEP_SECONDS)
            else -> 1.0
        }
        val shift = GOLDEN_CLOCKS.map { Math.floorMod(it - real + HALF_DAY, DAY_TICKS) - HALF_DAY }.minBy { abs(it) }
        return Math.floorMod(real + (shift * weight).toLong(), DAY_TICKS)
    }

    private fun spinAngle(entityId: Int): Float {
        if (spinAt == 0L || entityId != avatarId) return 0f
        val progress = since(spinAt) / SPIN_SECONDS
        if (progress >= 1.0) {
            spinAt = 0L
            return 0f
        }
        return (Easing.smoothstep(progress) * 2.0 * PI).toFloat()
    }

    //? if > 1.8.9 {
    @JvmStatic
    fun spin(entityId: Int, poseStack: PoseStack) {
        val angle = spinAngle(entityId)
        if (angle != 0f) poseStack.rotateBy(Quaternionf().rotationY(angle))
    }
    //?} else {
    /*@JvmStatic
    fun spin(entityId: Int) {
        val angle = spinAngle(entityId)
        if (angle != 0f) GlStateManager.rotatef(Math.toDegrees(angle.toDouble()).toFloat(), 0f, 1f, 0f)
    }
    *///?}

    fun sight(avatar: LobbyAvatar, feet: Vec3, head: Vec3, side: Vec3) {
        avatarId = avatar.id
        centerX = ((feet.x + head.x) / 2.0).toFloat()
        halfWidth = abs(side.x - (feet.x + head.x) / 2.0).toFloat()
        bottom = minOf(feet.y, head.y).toFloat()
        top = maxOf(feet.y, head.y).toFloat()
    }

    fun lose() {
        avatarId = -1
    }

    fun click(x: Double, y: Double): Boolean {
        if (avatarId < 0 || !ready()) return false
        val ndcX = x * 2.0 - 1.0
        val ndcY = 1.0 - y * 2.0
        if (abs(ndcX - centerX) > halfWidth || ndcY < bottom || ndcY > top) return false
        if (crouchTicks > 0) return true
        val now = System.nanoTime()
        if (lastClick == 0L || since(lastClick, now) > CLICK_GAP_SECONDS) streak = 0
        lastClick = now
        streak++
        when (streak) {
            WAVE_CLICKS -> waveQueued = true
            CROUCH_CLICKS -> {
                streak = 0
                waveQueued = false
                crouchTicks = CROUCH_TICKS
            }
        }
        return true
    }

    fun tick(avatar: LobbyAvatar?) {
        if (avatar == null) {
            waveQueued = false
            crouchTicks = 0
            return
        }
        if (waveQueued) {
            waveQueued = false
            LobbyEntrance.wave(avatar)
        }
        if (crouchTicks <= 0) return
        if (crouchTicks == CROUCH_TICKS) (avatar as PlayerEmotesAccess).`polyplus$emoteController`().stop()
        crouchTicks--
        val down = crouchTicks > 0 && (CROUCH_TICKS - crouchTicks - 1) / CROUCH_STEP_TICKS % 2 == 0
        avatar.pose = if (down) Pose.CROUCHING else Pose.STANDING
    }

    fun reset() {
        typed = 0
        goldenAt = 0L
        spinAt = 0L
        avatarId = -1
        streak = 0
        waveQueued = false
        crouchTicks = 0
    }
}
