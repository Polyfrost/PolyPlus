package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.EmoteRevision
import org.polyfrost.polyplus.client.emotes.EmoteLoads

class EmoteLoadsTest {
    private val a = EmoteRevision(7, "a")
    private val b = EmoteRevision(7, "b")

    @Test
    fun `a failed load retries when the revision changed`() {
        assertTrue(EmoteLoads.retry(a, b, loaded = false, attemptsLeft = 3))
    }

    @Test
    fun `a failed load of the current revision gives up`() {
        assertFalse(EmoteLoads.retry(a, a, loaded = false, attemptsLeft = 3))
    }

    @Test
    fun `a load that finished but isn't ready retries`() {
        assertTrue(EmoteLoads.retry(a, a, loaded = true, attemptsLeft = 2))
    }

    @Test
    fun `a definition that appears after a missing one retries`() {
        assertTrue(EmoteLoads.retry(null, a, loaded = false, attemptsLeft = 2))
    }

    @Test
    fun `retries stop on the last attempt`() {
        assertFalse(EmoteLoads.retry(a, b, loaded = false, attemptsLeft = 1))
        assertFalse(EmoteLoads.retry(a, a, loaded = true, attemptsLeft = 1))
    }

    @Test
    fun `a wheel slot waits for its emote before it can be picked`() {
        assertEquals(EmoteLoads.WheelSlot.Loading, EmoteLoads.wheelSlot(defined = true, ready = false, loading = true, duo = false))
        assertEquals(EmoteLoads.WheelSlot.Unloaded, EmoteLoads.wheelSlot(defined = true, ready = false, loading = false, duo = false))
    }

    @Test
    fun `a loaded wheel slot is classified by its current revision`() {
        assertEquals(EmoteLoads.WheelSlot.Duo, EmoteLoads.wheelSlot(defined = true, ready = true, loading = false, duo = true))
        assertEquals(EmoteLoads.WheelSlot.Solo, EmoteLoads.wheelSlot(defined = true, ready = true, loading = true, duo = false))
        assertEquals(EmoteLoads.WheelSlot.Empty, EmoteLoads.wheelSlot(defined = false, ready = true, loading = false, duo = false))
    }
}
