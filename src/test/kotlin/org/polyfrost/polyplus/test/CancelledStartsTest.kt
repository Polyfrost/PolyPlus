package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emotes.duo.CancelledStarts
import java.util.UUID

class CancelledStartsTest {
    private val partner = UUID(1L, 1L)
    private val stranger = UUID(2L, 2L)

    @Test
    fun `a cancelled start matches its emote and partner until it expires`() {
        val starts = CancelledStarts(windowMs = 1_000L)
        starts.add(7, partner, now = 0L)
        assertTrue(starts.matches(7, partner, now = 500L))
        assertTrue(starts.matches(7, null, now = 500L))
        assertFalse(starts.matches(8, partner, now = 500L))
        assertFalse(starts.matches(7, stranger, now = 500L))
        assertFalse(starts.matches(7, partner, now = 1_000L))
    }

    @Test
    fun `a new request with the partner forgets the cancelled start`() {
        val starts = CancelledStarts()
        starts.add(7, partner, now = 0L)
        starts.add(7, stranger, now = 0L)
        starts.forget(partner)
        assertFalse(starts.matches(7, partner, now = 1L))
        assertTrue(starts.matches(7, stranger, now = 1L))
    }

    @Test
    fun `only the newest cancelled starts are kept`() {
        val starts = CancelledStarts(limit = 2)
        starts.add(1, partner, now = 0L)
        starts.add(2, partner, now = 0L)
        starts.add(3, partner, now = 0L)
        assertFalse(starts.matches(1, partner, now = 1L))
        assertTrue(starts.matches(2, partner, now = 1L))
        assertTrue(starts.matches(3, partner, now = 1L))
    }
}
