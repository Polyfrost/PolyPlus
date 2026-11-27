package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emotes.duo.MarkerGhostRules

class MarkerGhostRulesTest {
    @Test
    fun `the start pose is held past the role delay and the blend in`() {
        assertEquals(150L, MarkerGhostRules.poseMillis(0f))
        assertEquals(1150L, MarkerGhostRules.poseMillis(1f))
        assertEquals(150L, MarkerGhostRules.poseMillis(-2f))
    }

    @Test
    fun `the ghost materializes over its appear time`() {
        assertEquals(0.0, MarkerGhostRules.appear(0L))
        assertEquals(0.5, MarkerGhostRules.appear(MarkerGhostRules.APPEAR_MS / 2))
        assertEquals(1.0, MarkerGhostRules.appear(MarkerGhostRules.APPEAR_MS))
        assertEquals(1.0, MarkerGhostRules.appear(MarkerGhostRules.APPEAR_MS * 5))
        assertEquals(0.0, MarkerGhostRules.appear(-40L))
    }

    @Test
    fun `a mark goes stale when the hud stops refreshing it`() {
        assertTrue(MarkerGhostRules.fresh(1_000L, 1_000L))
        assertTrue(MarkerGhostRules.fresh(1_000L, 1_000L + MarkerGhostRules.STALE_MS))
        assertFalse(MarkerGhostRules.fresh(1_000L, 1_001L + MarkerGhostRules.STALE_MS))
        assertFalse(MarkerGhostRules.fresh(Long.MIN_VALUE, 1_000L))
        assertFalse(MarkerGhostRules.fresh(2_000L, 1_000L))
    }

    @Test
    fun `the ghost hides while the camera is inside it`() {
        assertTrue(MarkerGhostRules.surrounds(10.0, 65.62, 20.0, 10.0, 64.0, 20.0))
        assertTrue(MarkerGhostRules.surrounds(10.5, 65.0, 20.3, 10.0, 64.0, 20.0))
        assertFalse(MarkerGhostRules.surrounds(11.0, 65.62, 20.0, 10.0, 64.0, 20.0))
        assertFalse(MarkerGhostRules.surrounds(10.0, 67.0, 20.0, 10.0, 64.0, 20.0))
        assertFalse(MarkerGhostRules.surrounds(10.0, 62.0, 20.0, 10.0, 64.0, 20.0))
    }
}
