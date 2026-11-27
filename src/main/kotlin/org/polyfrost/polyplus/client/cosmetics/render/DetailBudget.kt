package org.polyfrost.polyplus.client.cosmetics.render

import org.polyfrost.plusrenderer.Renderer

internal class DetailBudget(
    private val budgetNanos: Long = BUDGET_NANOS,
    private val raiseFrames: Int = RAISE_FRAMES,
    private val recoverFrames: Int = RECOVER_FRAMES,
) {
    var pressure = 0
        private set

    var averageNanos = 0.0
        private set

    private var over = 0
    private var under = 0

    fun sample(nanos: Long): Int {
        averageNanos += (nanos.coerceAtLeast(0L) - averageNanos) * SMOOTHING
        when {
            averageNanos > budgetNanos -> {
                under = 0
                if (++over >= raiseFrames) {
                    over = 0
                    if (pressure < MAX_PRESSURE) pressure++
                }
            }
            averageNanos < budgetNanos * RECOVER_FRACTION -> {
                over = 0
                if (++under >= recoverFrames) {
                    under = 0
                    if (pressure > 0) pressure--
                }
            }
            else -> {
                over = 0
                under = 0
            }
        }
        return pressure
    }

    fun reset() {
        pressure = 0
        averageNanos = 0.0
        over = 0
        under = 0
    }

    companion object {
        const val BUDGET_NANOS = 1_500_000L
        const val RAISE_FRAMES = 15
        const val RECOVER_FRAMES = 120
        const val RECOVER_FRACTION = 0.6
        const val SMOOTHING = 0.125
        const val FULL_STEPS = 3
        const val REDUCED_KEEP = 32
        const val MAX_PRESSURE = FULL_STEPS + 3
        const val MINIMAL_DISTANCE_SQ = 64.0 * 64.0

        fun level(order: Int, distanceSq: Double, fullDistanceSq: Double, fullLimit: Int, pressure: Int): Int {
            val distance = if (distanceSq.isNaN()) Double.POSITIVE_INFINITY else distanceSq
            if (distance > MINIMAL_DISTANCE_SQ) return Renderer.DETAIL_MINIMAL
            if (pressure > FULL_STEPS && order >= REDUCED_KEEP shr (pressure - FULL_STEPS - 1)) return Renderer.DETAIL_MINIMAL
            val full = fullLimit shr pressure.coerceAtMost(FULL_STEPS)
            return if (distance <= fullDistanceSq && order < full) Renderer.DETAIL_FULL else Renderer.DETAIL_REDUCED
        }
    }
}
