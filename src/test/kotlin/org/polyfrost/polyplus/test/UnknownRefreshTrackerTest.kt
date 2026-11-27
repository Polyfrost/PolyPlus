package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.UnknownRefreshTracker

class UnknownRefreshTrackerTest {
    @Test
    fun `an id still missing after a successful refresh is retried after a backoff`() {
        val tracker = UnknownRefreshTracker(retryBaseMs = 1_000, retryMaxMs = 10_000)
        assertTrue(tracker.request(1, 0))
        assertFalse(tracker.request(1, 0))
        assertEquals(listOf(1), tracker.takePending())
        tracker.completed(listOf(1), success = true, now = 0)
        assertFalse(tracker.request(1, 999))
        assertTrue(tracker.request(1, 1_000))
        tracker.completed(tracker.takePending(), success = true, now = 1_000)
        assertFalse(tracker.request(1, 2_999))
        assertTrue(tracker.request(1, 3_000))
    }

    @Test
    fun `a failed refresh is retried after a backoff`() {
        val tracker = UnknownRefreshTracker(retryBaseMs = 1_000, retryMaxMs = 10_000)
        tracker.request(1, 0)
        tracker.completed(tracker.takePending(), success = false, now = 0)
        assertFalse(tracker.request(1, 999))
        assertTrue(tracker.request(1, 1_000))
        tracker.completed(tracker.takePending(), success = false, now = 1_000)
        assertFalse(tracker.request(1, 2_999))
        assertTrue(tracker.request(1, 3_000))
    }

    @Test
    fun `unresolved ids keep retrying at the capped interval`() {
        val tracker = UnknownRefreshTracker(retryBaseMs = 1_000, retryMaxMs = 10_000)
        var now = 0L
        repeat(20) { attempt ->
            assertTrue(tracker.request(1, now))
            tracker.completed(tracker.takePending(), success = attempt % 2 == 0, now = now)
            now += 10_000
        }
        assertFalse(tracker.request(1, now - 1))
        assertTrue(tracker.request(1, now))
        tracker.completed(tracker.takePending(), success = true, now = now) { true }
        assertEquals(0, tracker.size())
    }

    @Test
    fun `ids requested during a refresh wait for the next one`() {
        val tracker = UnknownRefreshTracker()
        tracker.request(1, 0)
        val inFlight = tracker.takePending()
        tracker.request(2, 0)
        tracker.completed(inFlight, success = true, now = 0)
        assertTrue(tracker.hasPending())
        assertEquals(listOf(2), tracker.takePending())
    }

    @Test
    fun `the tracked set is bounded`() {
        val tracker = UnknownRefreshTracker(capacity = 8)
        repeat(100) { tracker.request(it, 0) }
        assertEquals(8, tracker.size())
    }

    @Test
    fun `ids past the bound keep the tracked ids backing off`() {
        val tracker = UnknownRefreshTracker(capacity = 8, retryBaseMs = 1_000, retryMaxMs = 10_000)
        repeat(100) { tracker.request(it, 0) }
        tracker.completed(tracker.takePending(), success = false, now = 0)
        for (pass in 1..5) {
            val now = pass * 100L
            repeat(100) { assertFalse(tracker.request(it, now)) }
            assertFalse(tracker.hasPending())
        }
        assertEquals(8, tracker.size())
        assertTrue(tracker.request(0, 1_000))
        assertFalse(tracker.request(50, 1_000))
        assertEquals(listOf(0), tracker.takePending())
    }

    @Test
    fun `a known id frees room for a new one`() {
        val tracker = UnknownRefreshTracker(capacity = 2)
        tracker.request(1, 0)
        tracker.request(2, 0)
        assertFalse(tracker.request(3, 0))
        tracker.completed(tracker.takePending(), success = true, now = 0) { it == 1 }
        assertTrue(tracker.request(3, 0))
    }

    @Test
    fun `a separate catalog load frees ids it made known`() {
        val tracker = UnknownRefreshTracker(capacity = 2)
        tracker.request(1, 0)
        tracker.request(2, 0)
        tracker.completed(tracker.takePending(), success = false, now = 0)
        assertFalse(tracker.request(3, 0))
        tracker.forgetKnown { it == 1 }
        assertEquals(1, tracker.size())
        assertTrue(tracker.request(3, 0))
    }

    @Test
    fun `failed ids nobody asks about any more are reclaimed when full`() {
        val tracker = UnknownRefreshTracker(capacity = 2, retryBaseMs = 10_000, retryMaxMs = 10_000, staleMs = 5_000)
        tracker.request(1, 0)
        tracker.request(2, 0)
        tracker.completed(tracker.takePending(), success = false, now = 0)
        assertFalse(tracker.request(2, 4_000))
        assertFalse(tracker.request(3, 4_999))
        assertTrue(tracker.request(3, 5_000))
        assertEquals(2, tracker.size())
        assertFalse(tracker.request(1, 5_000))
    }

    @Test
    fun `refreshes that resolve nothing back off globally up to the cap`() {
        val tracker = UnknownRefreshTracker(retryBaseMs = 1_000, retryMaxMs = 4_000)
        var now = 0L
        val waits = ArrayList<Long>()
        repeat(5) {
            tracker.request(100 + it, now)
            tracker.completed(tracker.takePending(), success = true, now = now)
            waits += tracker.nextRefreshAtMs() - now
            now += 10_000
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 4_000L, 4_000L), waits)
        tracker.request(1, now)
        tracker.completed(tracker.takePending(), success = true, now = now) { true }
        assertEquals(Long.MIN_VALUE, tracker.nextRefreshAtMs())
    }

    @Test
    fun `dropped ids can be asked for again straight away`() {
        val tracker = UnknownRefreshTracker(retryBaseMs = 1_000, retryMaxMs = 10_000)
        tracker.request(1, 0)
        tracker.request(2, 0)
        val taken = tracker.takePending()
        tracker.drop(taken)
        assertEquals(0, tracker.size())
        assertTrue(tracker.request(1, 0))
        tracker.drop(listOf(1))
        assertTrue(tracker.hasPending())
    }
}
