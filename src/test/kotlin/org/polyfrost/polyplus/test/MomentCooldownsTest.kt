package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.moments.MomentCooldowns
import java.util.UUID

class MomentCooldownsTest {
    private val wearer = UUID(1L, 2L)

    @Test
    fun `a fired moment cools down for its window`() {
        val cooldowns = MomentCooldowns(moments = 3, windowMs = 10_000)
        cooldowns.record(wearer, 0, 1_000)
        assertTrue(cooldowns.coolingDown(wearer, 0, 5_000))
        assertFalse(cooldowns.coolingDown(wearer, 1, 5_000))
        assertFalse(cooldowns.coolingDown(wearer, 0, 11_000))
    }

    @Test
    fun `a cooldown outlives pruning until its window ends`() {
        val cooldowns = MomentCooldowns(moments = 3, windowMs = 10_000)
        cooldowns.record(wearer, 0, 1_000)
        cooldowns.prune(4_000)
        assertTrue(cooldowns.coolingDown(wearer, 0, 6_000))
        assertEquals(1, cooldowns.size())
        cooldowns.prune(11_000)
        assertEquals(0, cooldowns.size())
    }

    @Test
    fun `a wearer is kept while any moment still cools down`() {
        val cooldowns = MomentCooldowns(moments = 3, windowMs = 10_000)
        cooldowns.record(wearer, 0, 1_000)
        cooldowns.record(wearer, 2, 8_000)
        cooldowns.prune(12_000)
        assertEquals(1, cooldowns.size())
        assertTrue(cooldowns.coolingDown(wearer, 2, 12_000))
    }
}
