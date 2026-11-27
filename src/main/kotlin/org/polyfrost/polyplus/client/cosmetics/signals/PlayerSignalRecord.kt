package org.polyfrost.polyplus.client.cosmetics.signals

import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.sqrt

class PlayerSignalRecord internal constructor() {
    internal val values = FloatArray(PlayerSignals.QUERY_COUNT)
    private val raisedSerial = IntArray(EVENT_BITS)
    private val raisedNanos = LongArray(EVENT_BITS)
    private val readerSerial = IntArray(PlayerSignals.READERS)
    private var eventSerial = 0
    internal var tick = 0
    internal var seen = 0L
    internal var airTicks = 0
    private var died = false
    private var entityRef: WeakReference<Any>? = null
    private var levelRef: WeakReference<Any>? = null
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var lastYaw = 0f
    private val speeds = FloatArray(SMOOTHING_TICKS)
    private var speedCount = 0
    private var speedIndex = 0
    private var hurtTime = 0
    private var deathTime = 0
    private var sleeping = false
    private var jumpEntity: WeakReference<Any>? = null
    private var jumpTick = 0
    private var jumpY = 0.0
    private var jumpGrounded = false
    private var jumpRising = false
    private var prevGround = 0f
    private var curGround = 0f
    private var prevVertical = 0f
    private var curVertical = 0f
    private var prevYawSpeed = 0f
    private var curYawSpeed = 0f
    private var prevTime = 0f
    private var curTime = 0f

    operator fun get(signal: Int): Float = values[signal]

    fun flag(signal: Int): Boolean = values[signal] != 0f

    fun query(index: Int): Float = values[index]

    fun query(name: String): Float {
        val index = PlayerSignals.queryIndex(name)
        return if (index < 0) 0f else values[index]
    }

    fun events(reader: Int): Int {
        val last = readerSerial[reader]
        if (last == eventSerial) return 0
        readerSerial[reader] = eventSerial
        val now = System.nanoTime()
        var bits = 0
        for (bit in 0 until EVENT_BITS) {
            if (raisedSerial[bit] - last > 0 && now - raisedNanos[bit] <= EVENT_LIFETIME_NANOS) bits = bits or (1 shl bit)
        }
        return bits
    }

    internal fun raise(bits: Int) {
        if (bits == 0) return
        eventSerial++
        val now = System.nanoTime()
        for (bit in 0 until EVENT_BITS) {
            if (bits and (1 shl bit) == 0) continue
            raisedSerial[bit] = eventSerial
            raisedNanos[bit] = now
        }
    }

    fun copyInto(out: FloatArray, offset: Int = 0) {
        values.copyInto(out, offset, 0, PlayerSignals.SIGNAL_COUNT)
    }

    internal val groundSpeed: Float get() = curGround

    internal val verticalSpeed: Float get() = curVertical

    internal fun isTracking(entity: Any, tick: Int): Boolean = entityRef?.get() === entity && this.tick == tick

    internal fun begin(entity: Any, level: Any, tick: Int, x: Double, y: Double, z: Double, yaw: Float): Int {
        val fresh = entityRef?.get() !== entity
        val previousLevel = levelRef?.get()
        val changedLevel = previousLevel != null && previousLevel !== level
        if (fresh && died) {
            raise(PlayerSignals.EVENT_RESPAWN)
            died = false
        }
        if (changedLevel) raise(PlayerSignals.EVENT_DIMENSION_CHANGE)

        val elapsed: Int
        if (fresh || changedLevel || previousLevel == null) {
            if (fresh) entityRef = WeakReference(entity)
            if (previousLevel !== level) levelRef = WeakReference(level)
            speedCount = 0
            speedIndex = 0
            airTicks = 0
            hurtTime = 0
            deathTime = 0
            sleeping = false
            elapsed = 0
        } else {
            elapsed = max(1, tick - this.tick)
        }
        this.tick = tick

        var horizontal = 0f
        var vertical = 0f
        var yawSpeed = 0f
        if (elapsed > 0) {
            val dx = x - lastX
            val dz = z - lastZ
            val perSecond = PlayerSignals.TICKS_PER_SECOND / elapsed
            horizontal = (sqrt(dx * dx + dz * dz) * perSecond).toFloat()
            vertical = ((y - lastY) * perSecond).toFloat()
            yawSpeed = (wrapDegrees(yaw - lastYaw) * perSecond).toFloat()
            speeds[speedIndex] = horizontal
            speedIndex = (speedIndex + 1) % SMOOTHING_TICKS
            if (speedCount < SMOOTHING_TICKS) speedCount++
        }
        lastX = x
        lastY = y
        lastZ = z
        lastYaw = yaw

        var ground = 0f
        if (speedCount > 0) {
            for (i in 0 until speedCount) ground += speeds[i]
            ground /= speedCount
        }
        if (elapsed == 0) {
            prevGround = ground
            prevVertical = vertical
            prevYawSpeed = yawSpeed
        } else {
            prevGround = curGround
            prevVertical = curVertical
            prevYawSpeed = curYawSpeed
        }
        curGround = ground
        curVertical = vertical
        curYawSpeed = yawSpeed
        return elapsed
    }

    internal fun airborne(airborne: Boolean, elapsed: Int) {
        airTicks = if (airborne) airTicks + max(1, elapsed) else 0
    }

    internal fun edges(hurtTime: Int, deathTime: Int, sleeping: Boolean) {
        var raised = 0
        if (hurtTime > this.hurtTime) raised = raised or PlayerSignals.EVENT_HURT
        if (deathTime > 0 && this.deathTime == 0) {
            raised = raised or PlayerSignals.EVENT_DEATH
            died = true
        }
        if (sleeping && !this.sleeping) raised = raised or PlayerSignals.EVENT_SLEEP
        raise(raised)
        this.hurtTime = hurtTime
        this.deathTime = deathTime
        this.sleeping = sleeping
    }

    internal fun takeoff(entity: Any, tick: Int, y: Double, grounded: Boolean, free: Boolean) {
        if (jumpEntity?.get() !== entity) {
            jumpEntity = WeakReference(entity)
            jumpTick = tick
            jumpY = y
            jumpGrounded = grounded
            jumpRising = false
            return
        }
        if (tick == jumpTick) return
        jumpTick = tick
        val rising = !grounded && free && y > jumpY
        if (rising && !jumpRising && jumpGrounded) raise(PlayerSignals.EVENT_JUMP)
        jumpRising = rising
        jumpGrounded = grounded
        jumpY = y
    }

    internal fun timeOfDay(fraction: Float, elapsed: Int) {
        prevTime = if (elapsed == 0) fraction else curTime
        curTime = fraction
    }

    internal fun set(signal: Int, value: Boolean) {
        values[signal] = if (value) 1f else 0f
    }

    internal fun set(signal: Int, value: Float) {
        values[signal] = value
    }

    internal fun interpolate(partialTick: Float) {
        val t = partialTick.coerceIn(0f, 1f)
        values[PlayerSignals.GROUND_SPEED] = prevGround + (curGround - prevGround) * t
        values[PlayerSignals.VERTICAL_SPEED] = prevVertical + (curVertical - prevVertical) * t
        values[PlayerSignals.YAW_SPEED] = prevYawSpeed + (curYawSpeed - prevYawSpeed) * t
        val end = if (curTime < prevTime) curTime + 1f else curTime
        val time = prevTime + (end - prevTime) * t
        values[PlayerSignals.TIME_OF_DAY] = if (time >= 1f) time - 1f else time
    }

    private fun wrapDegrees(degrees: Float): Float {
        var wrapped = degrees % 360f
        if (wrapped >= 180f) wrapped -= 360f
        if (wrapped < -180f) wrapped += 360f
        return wrapped
    }

    private companion object {
        const val SMOOTHING_TICKS = 3
        const val EVENT_BITS = 7
        const val EVENT_LIFETIME_NANOS = 1_000_000_000L
    }
}
