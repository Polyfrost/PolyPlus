package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.gui.HeadFetchPolicy

class HeadFetchPolicyTest {
    @Test
    fun `an account with no profile is never retried`() {
        assertTrue(HeadFetchPolicy.isDefinitiveMiss(204))
        assertTrue(HeadFetchPolicy.isDefinitiveMiss(404))
    }

    @Test
    fun `a rate limit or a server fault is retried, not treated as a missing account`() {
        assertFalse(HeadFetchPolicy.isDefinitiveMiss(429))
        assertFalse(HeadFetchPolicy.isDefinitiveMiss(500))
        assertFalse(HeadFetchPolicy.isDefinitiveMiss(503))
    }

    @Test
    fun `the wait grows with each attempt and stops at the ceiling`() {
        val delays = (1..12).map { HeadFetchPolicy.retryDelayMs(it, jitterMs = 0) }

        assertEquals(HeadFetchPolicy.RETRY_BASE_MS, delays.first())
        assertEquals(HeadFetchPolicy.RETRY_BASE_MS * 2, delays[1])
        delays.zipWithNext { earlier, later -> assertTrue(later >= earlier, "$later came before $earlier") }
        assertTrue(delays.all { it <= HeadFetchPolicy.RETRY_MAX_MS }, "a delay ran past the ceiling: $delays")
        assertEquals(HeadFetchPolicy.RETRY_MAX_MS, delays.last())
    }

    @Test
    fun `the ladder gives up rather than retrying for the rest of the run`() {
        assertTrue(HeadFetchPolicy.shouldRetry(1))
        assertTrue(HeadFetchPolicy.shouldRetry(HeadFetchPolicy.MAX_ATTEMPTS - 1))
        assertFalse(HeadFetchPolicy.shouldRetry(HeadFetchPolicy.MAX_ATTEMPTS))
        assertFalse(HeadFetchPolicy.shouldRetry(HeadFetchPolicy.MAX_ATTEMPTS + 1))
    }

    @Test
    fun `the whole ladder is walked in a bounded stretch of time`() {
        val total = (1 until HeadFetchPolicy.MAX_ATTEMPTS).sumOf { HeadFetchPolicy.retryDelayMs(it, jitterMs = 0) }
        assertTrue(total < 30 * 60 * 1000L, "the ladder runs for ${total}ms before giving up")
    }

    @Test
    fun `jitter keeps a list of unresolved accounts from retrying in lockstep`() {
        val delays = List(200) { HeadFetchPolicy.retryDelayMs(1) }

        assertTrue(delays.distinct().size > 1, "every retry got the same delay, so they will all fire together")
        assertTrue(
            delays.all { it in HeadFetchPolicy.RETRY_BASE_MS..(HeadFetchPolicy.RETRY_BASE_MS + HeadFetchPolicy.RETRY_JITTER_MS) },
            "jitter pushed a delay outside its window: ${delays.minOrNull()}..${delays.maxOrNull()}",
        )
    }
}
