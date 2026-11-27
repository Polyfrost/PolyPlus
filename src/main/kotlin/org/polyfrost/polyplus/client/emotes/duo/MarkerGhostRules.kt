package org.polyfrost.polyplus.client.emotes.duo

import kotlin.math.max
import kotlin.math.roundToLong

internal object MarkerGhostRules {
    const val APPEAR_MS = 600L
    const val STALE_MS = 500L
    private const val IDLE_TINT = 0x8AD4FF
    private const val READY_TINT = 0x7BE495
    private const val POSE_LEAD_SECONDS = 0.15f
    private const val MS_PER_SECOND = 1000f
    private const val CLEARANCE = 0.7
    private const val BELOW = 0.5
    private const val HEIGHT = 2.0

    fun poseMillis(delaySeconds: Float): Long = ((max(delaySeconds, 0f) + POSE_LEAD_SECONDS) * MS_PER_SECOND).roundToLong()

    fun appear(elapsedMs: Long): Double = (elapsedMs.toDouble() / APPEAR_MS).coerceIn(0.0, 1.0)

    fun fresh(markedAt: Long, now: Long): Boolean {
        if (markedAt > now) return false
        val age = now - markedAt
        return age in 0..STALE_MS
    }

    fun surrounds(cameraX: Double, cameraY: Double, cameraZ: Double, x: Double, y: Double, z: Double): Boolean {
        val dx = cameraX - x
        val dz = cameraZ - z
        return dx * dx + dz * dz < CLEARANCE * CLEARANCE && cameraY >= y - BELOW && cameraY <= y + HEIGHT
    }

    fun tint(ready: Boolean): Int = if (ready) READY_TINT else IDLE_TINT
}
