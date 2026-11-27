package org.polyfrost.polyplus.client.utils

object CosmeticClock {
    private val startNanos = System.nanoTime()
    private val startSeconds = System.currentTimeMillis() / 1000.0

    @JvmStatic
    fun seconds(): Double = startSeconds + (System.nanoTime() - startNanos) / 1_000_000_000.0
}
