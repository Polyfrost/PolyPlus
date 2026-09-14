package org.polyfrost.polyplus.client.gui

internal object HeadFetchPolicy {
    const val RETRY_BASE_MS = 30_000L
    const val RETRY_MAX_MS = 10L * 60 * 1000
    const val RETRY_JITTER_MS = 5_000L

    const val MAX_ATTEMPTS = 6

    private const val MAX_DOUBLINGS = 5

    fun isDefinitiveMiss(responseCode: Int): Boolean = responseCode == 204 || responseCode == 404

    fun shouldRetry(attempt: Int): Boolean = attempt < MAX_ATTEMPTS

    fun retryDelayMs(attempt: Int, jitterMs: Long = (0..RETRY_JITTER_MS).random()): Long =
        (RETRY_BASE_MS shl (attempt - 1).coerceIn(0, MAX_DOUBLINGS)).coerceAtMost(RETRY_MAX_MS) + jitterMs
}
