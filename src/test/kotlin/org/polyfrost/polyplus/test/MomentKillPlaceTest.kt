package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.moments.killPlaceKnown
import org.polyfrost.polyplus.client.cosmetics.moments.killVictimHidden

class MomentKillPlaceTest {
    private val now = 1_000_000L

    @Test
    fun `a victim that was never seen has no kill position`() {
        assertFalse(killPlaceKnown(hidden = false, present = false, seenAt = Long.MIN_VALUE, now = now))
    }

    @Test
    fun `a victim seen moments ago keeps its position`() {
        assertTrue(killPlaceKnown(hidden = false, present = false, seenAt = now - 2_000L, now = now))
    }

    @Test
    fun `a victim seen too long ago has no kill position`() {
        assertFalse(killPlaceKnown(hidden = false, present = false, seenAt = now - 6_000L, now = now))
    }

    @Test
    fun `an invisible or spectating victim shows no kill effect`() {
        assertFalse(killPlaceKnown(present = true, hidden = true, seenAt = now, now = now))
        assertFalse(killPlaceKnown(present = false, hidden = true, seenAt = now - 1_000L, now = now))
    }

    @Test
    fun `a present victim always has a position`() {
        assertTrue(killPlaceKnown(hidden = false, present = true, seenAt = Long.MIN_VALUE, now = now))
    }

    @Test
    fun `a victim's live visibility wins over the last tick's`() {
        assertTrue(killVictimHidden(live = true, cached = false))
        assertFalse(killVictimHidden(live = false, cached = true))
    }

    @Test
    fun `a victim no longer loaded uses its last seen visibility`() {
        assertTrue(killVictimHidden(live = null, cached = true))
        assertFalse(killVictimHidden(live = null, cached = false))
    }
}
