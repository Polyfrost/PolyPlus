package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.player.LocalPlayer
import org.polyfrost.oneconfig.api.platform.v1.Platform
import kotlin.math.hypot

object LobbyCaptureGate {
    private const val CATCH_UP_NANOS = 25_000_000L
    private const val COMBAT_TICKS = 200
    private const val SPEED_WINDOW = 20
    private const val FAST_BLOCKS = 10.0
    private const val MIN_FPS = 20
    private const val FPS_DROP = 0.6
    private const val FPS_EASE = 0.01

    private val trailX = IntArray(SPEED_WINDOW)
    private val trailZ = IntArray(SPEED_WINDOW)
    private var trailed = 0
    private var head = 0
    private var sinceHurt = COMBAT_TICKS
    private var fps = 0
    private var baseline = 0.0
    private var lastStep = 0L

    var calm = true
        private set

    fun tick(player: LocalPlayer) {
        val feet = player.blockPosition()
        val oldest = (head + 1) % SPEED_WINDOW
        val moved = if (trailed >= SPEED_WINDOW) hypot((feet.x - trailX[oldest]).toDouble(), (feet.z - trailZ[oldest]).toDouble()) else 0.0
        head = oldest
        trailX[head] = feet.x
        trailZ[head] = feet.z
        if (trailed < SPEED_WINDOW) trailed++
        sinceHurt = (sinceHurt + 1).coerceAtMost(COMBAT_TICKS)
        //? if > 1.8.9
        if (player.hurtTime > 0) sinceHurt = 0
        fps = runCatching { Platform.compatibility().fps() }.getOrDefault(0)
        val strained = fps in 1 until MIN_FPS || (baseline > 0.0 && fps > 0 && fps < baseline * FPS_DROP)
        if (fps > 0) baseline = if (baseline <= 0.0) fps.toDouble() else baseline + (fps - baseline) * FPS_EASE
        calm = !strained && sinceHurt >= COMBAT_TICKS && moved <= FAST_BLOCKS
    }

    fun reset() {
        trailed = 0
        sinceHurt = COMBAT_TICKS
        calm = true
    }

    fun spread(): Boolean {
        val now = System.nanoTime()
        if (now - lastStep < CATCH_UP_NANOS) return false
        lastStep = now
        return true
    }
}
