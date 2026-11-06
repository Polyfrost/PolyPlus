package org.polyfrost.polyplus.client.lobby

import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class LobbyEase(var value: Double = 0.0) {
    private var velocity = 0.0

    fun step(target: Double, rate: Double, dt: Double) {
        val offset = value - target
        val carry = (velocity + rate * offset) * dt
        val decay = exp(-rate * dt)
        value = target + (offset + carry) * decay
        velocity = (velocity - rate * carry) * decay
    }

    fun snap(target: Double) {
        value = target
        velocity = 0.0
    }
}

class LobbyGaze(private val seed: Int) {
    private class Spring {
        var value = 0f
        var velocity = 0f

        fun step(target: Float, dt: Float, frequency: Float, damping: Float) {
            var left = dt
            while (left > 0f) {
                val h = min(left, SUBSTEP)
                velocity += (frequency * frequency * (target - value) - 2f * damping * frequency * velocity) * h
                value += velocity * h
                left -= h
            }
        }

        fun adopt(actual: Float, keepVelocity: Boolean) {
            value = actual
            if (!keepVelocity) velocity = 0f
        }
    }

    private var random = Random(seed)
    private val yaw = Spring()
    private val pitch = Spring()
    private val body = Spring()
    private var bodyAim = 0f
    private var aimYaw = 0f
    private var aimPitch = 0f
    private var aimBody = 0f
    private var quick = false
    private var nod = 0f
    private var synced = false
    private var wroteYaw = 0f
    private var wrotePitch = 0f
    private var wroteBody = 0f
    private var clock = -1.0
    private var shiftAt = 0.0
    private var holdYaw = 0f
    private var holdPitch = 0f
    private val phase = random.nextDouble(0.0, Math.PI * 2.0)
    private var waveAt = -1.0
    private var waveLength = 0.0
    private var wavedNanos = 0L

    var wanderYaw = 0f
        private set
    var wanderPitch = 0f
        private set

    var driven = false

    fun wander(time: Double, range: Float, anchor: Double = time) {
        if (driven) {
            if (clock < 0.0 || time < clock) restart(anchor)
            val target = min(time, MAX_DRIVEN_SECONDS)
            while (target >= shiftAt) {
                shift(range)
                shiftAt += random.nextDouble(HOLD_MIN, HOLD_MAX)
            }
        } else {
            if (clock < 0.0 || time < clock - 1.0) {
                shiftAt = time + random.nextDouble(FIRST_HOLD_MIN, FIRST_HOLD_MAX)
                holdYaw = 0f
                holdPitch = 0f
            }
            if (time >= shiftAt) {
                shift(range)
                shiftAt = time + random.nextDouble(HOLD_MIN, HOLD_MAX)
            }
        }
        clock = time
        wanderYaw = holdYaw + (sin(time * BREATH_YAW_RATE + phase) * BREATH_YAW).toFloat()
        wanderPitch = holdPitch + (sin(time * BREATH_PITCH_RATE + phase * 0.7) * BREATH_PITCH).toFloat()
    }

    private fun restart(anchor: Double) {
        random = Random(seed)
        random.nextDouble(0.0, Math.PI * 2.0)
        shiftAt = anchor + random.nextDouble(FIRST_HOLD_MIN, FIRST_HOLD_MAX)
        holdYaw = 0f
        holdPitch = 0f
    }

    private fun shift(range: Float) {
        holdYaw = if (random.nextFloat() < FRONT_CHANCE) {
            random.nextFloat() * FRONT_SPREAD * 2f - FRONT_SPREAD
        } else {
            val side = if (holdYaw > 0f == random.nextFloat() < SWITCH_CHANCE) -1f else 1f
            side * (MIN_GLANCE + random.nextFloat() * (range - MIN_GLANCE))
        }
        holdPitch = PITCH_LOW + random.nextFloat() * (PITCH_HIGH - PITCH_LOW)
    }

    fun aim(yaw: Float, pitch: Float, body: Float, quick: Boolean = false, nod: Float = 0f) {
        aimYaw = yaw
        aimPitch = pitch
        aimBody = body
        this.quick = quick
        this.nod = nod
    }

    fun wave(length: Double) {
        waveAt = if (driven) LobbyScene.motionSeconds() else clock
        waveLength = length
        wavedNanos = System.nanoTime()
    }

    fun wavedWithin(seconds: Double): Boolean = when {
        wavedNanos == 0L -> false
        driven -> LobbyScene.motionSeconds() - waveAt < waveLength + seconds
        else -> (System.nanoTime() - wavedNanos) / 1_000_000_000.0 < waveLength + seconds
    }

    fun greeting(time: Double): Float {
        if (waveAt < 0.0) return 0f
        val since = time - waveAt
        if (since < 0.0 || since > waveLength + GREET_OUT) return 0f
        val rise = smooth((since / GREET_IN).toFloat())
        val fall = 1f - smooth(((since - waveLength + GREET_HOLD_END) / (GREET_OUT + GREET_HOLD_END)).toFloat())
        return min(rise, fall)
    }

    fun greet(weight: Float) {
        aimBody += GREET_TWIST * weight
    }

    fun drive(avatar: LobbyAvatar, dt: Double) {
        val headNow = avatar.yHeadRot
        val pitchNow = avatar.xRot
        val bodyNow = avatar.yBodyRot
        if (!synced) {
            yaw.adopt(headNow, false)
            pitch.adopt(pitchNow, false)
            body.adopt(bodyNow, false)
            bodyAim = bodyNow
            synced = true
        } else {
            if (headNow != wroteYaw) yaw.adopt(headNow, abs(Mth.wrapDegrees(headNow - wroteYaw)) < KEEP_SPEED)
            if (pitchNow != wrotePitch) pitch.adopt(pitchNow - nod, abs(pitchNow - wrotePitch) < KEEP_SPEED)
            if (bodyNow != wroteBody) {
                body.adopt(bodyNow, abs(Mth.wrapDegrees(bodyNow - wroteBody)) < KEEP_SPEED)
                bodyAim = bodyNow
            }
        }
        val step = dt.toFloat().coerceIn(0f, MAX_DT)
        val frequency = if (quick) QUICK_FREQUENCY else HEAD_FREQUENCY
        val damping = if (quick) QUICK_DAMPING else HEAD_DAMPING
        yaw.step(yaw.value + Mth.wrapDegrees(aimYaw - yaw.value), step, frequency, damping)
        pitch.step(aimPitch, step, frequency, damping)
        bodyAim += Mth.wrapDegrees(aimBody - bodyAim) * (1f - exp(-step / BODY_LAG))
        body.step(body.value + Mth.wrapDegrees(bodyAim - body.value), step, BODY_FREQUENCY, BODY_DAMPING)
        wroteYaw = yaw.value
        wrotePitch = (pitch.value + nod).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        wroteBody = body.value
        avatar.yHeadRot = wroteYaw
        avatar.yHeadRotO = wroteYaw
        avatar.setXRot(wrotePitch)
        avatar.xRotO = wrotePitch
        avatar.yBodyRot = wroteBody
        avatar.yBodyRotO = wroteBody
    }

    companion object {
        private const val SUBSTEP = 1f / 240f
        private const val MAX_DRIVEN_SECONDS = 1.0e6
        private const val MAX_DT = 0.1f
        private const val KEEP_SPEED = 2f
        private const val HEAD_FREQUENCY = 8f
        private const val HEAD_DAMPING = 0.7f
        private const val QUICK_FREQUENCY = 13f
        private const val QUICK_DAMPING = 0.78f
        private const val BODY_FREQUENCY = 4.5f
        private const val BODY_DAMPING = 1f
        private const val BODY_LAG = 0.22f
        private const val PITCH_LIMIT = 89f
        private const val FIRST_HOLD_MIN = 0.6
        private const val FIRST_HOLD_MAX = 1.6
        private const val HOLD_MIN = 1.5
        private const val HOLD_MAX = 3.8
        private const val FRONT_CHANCE = 0.35f
        private const val FRONT_SPREAD = 5f
        private const val SWITCH_CHANCE = 0.7f
        private const val MIN_GLANCE = 10f
        private const val PITCH_LOW = -5f
        private const val PITCH_HIGH = 6f
        private const val BREATH_YAW = 0.8
        private const val BREATH_YAW_RATE = 0.9
        private const val BREATH_PITCH = 0.6
        private const val BREATH_PITCH_RATE = 1.3
        private const val GREET_IN = 0.3
        private const val GREET_HOLD_END = 0.35
        private const val GREET_OUT = 0.15
        private const val GREET_TWIST = 5f

        fun smooth(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
