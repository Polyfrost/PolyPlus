package org.polyfrost.polyplus.client.network.eos

object EosTickHealth {
    const val THREAD_NAME = "polyplus-eos"
    const val STALL_THRESHOLD_MS = 10_000L

    const val OBSERVER_GAP_TOLERANCE_MS = 2_000L

    fun monotonicMs(): Long = System.nanoTime() / 1_000_000

    fun isStalled(ticking: Boolean, lastTickMs: Long, nowMs: Long): Boolean =
        ticking && lastTickMs != 0L && nowMs - lastTickMs > STALL_THRESHOLD_MS

    fun isObservationTrustworthy(lastCheckMs: Long, nowMs: Long): Boolean =
        lastCheckMs != 0L && nowMs - lastCheckMs <= OBSERVER_GAP_TOLERANCE_MS
}
