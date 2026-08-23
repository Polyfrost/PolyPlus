package org.polyfrost.polyplus.client.network.eos

object EosTickHealth {
    const val THREAD_NAME = "polyplus-eos"
    const val STALL_THRESHOLD_MS = 10_000L

    fun isStalled(ticking: Boolean, lastTickMs: Long, nowMs: Long): Boolean =
        ticking && lastTickMs != 0L && nowMs - lastTickMs > STALL_THRESHOLD_MS
}
