package org.polyfrost.polyplus.client.network

import kotlin.random.Random

private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
private const val MAX_RECONNECT_DELAY_MS = 60_000L

// exponential backoff with jitter, capped at a minute
internal fun reconnectDelay(attempt: Int): Long {
    val shift = (attempt - 1).coerceIn(0, 30)
    val delayMs = INITIAL_RECONNECT_DELAY_MS shl shift
    val capped = if (delayMs <= 0L) MAX_RECONNECT_DELAY_MS else delayMs.coerceAtMost(MAX_RECONNECT_DELAY_MS)
    val half = capped / 2
    return half + Random.nextLong(half + 1)
}
