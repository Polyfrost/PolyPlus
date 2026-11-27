package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.AssetTrimTimer
import org.polyfrost.polyplus.client.cosmetics.sets.BonusState
import org.polyfrost.polyplus.client.network.http.responses.BonusRef

class AssetTrimTimerTest {
    private fun ticksUntilTrim(timer: AssetTrimTimer, limit: Int = 1000, beforeTick: (Int) -> Unit = {}): Int? {
        for (tick in 1..limit) {
            beforeTick(tick)
            if (timer.tick()) return tick
        }
        return null
    }

    @Test
    fun `nothing trims without a reason`() {
        assertEquals(null, ticksUntilTrim(AssetTrimTimer(20, 100)))
    }

    @Test
    fun `a departure trims after the departure delay`() {
        val timer = AssetTrimTimer(20, 100)
        timer.departed()
        assertEquals(100, ticksUntilTrim(timer))
    }

    @Test
    fun `steady departures still trim on schedule`() {
        val timer = AssetTrimTimer(20, 100)
        timer.departed()
        assertEquals(100, ticksUntilTrim(timer) { timer.departed() })
    }

    @Test
    fun `departures during the wait trim once`() {
        val timer = AssetTrimTimer(20, 100)
        timer.departed()
        var trims = 0
        for (tick in 1..150) {
            if (tick < 50) timer.departed()
            if (timer.tick()) trims++
        }
        assertEquals(1, trims)
    }

    @Test
    fun `a departure after a trim schedules another`() {
        val timer = AssetTrimTimer(20, 100)
        timer.departed()
        assertEquals(100, ticksUntilTrim(timer))
        timer.departed()
        assertEquals(100, ticksUntilTrim(timer))
    }

    @Test
    fun `a settle waits the full settle delay`() {
        val timer = AssetTrimTimer(20, 100)
        timer.departed()
        repeat(95) { assertFalse(timer.tick()) }
        timer.settle()
        assertEquals(20, ticksUntilTrim(timer))
    }

    @Test
    fun `a bonus counts as gone only when it leaves`() {
        val knight = BonusRef(3)
        val wizard = BonusRef(5)
        assertTrue(BonusState.dropped(listOf(knight, wizard), listOf(knight)))
        assertTrue(BonusState.dropped(listOf(knight), emptyList()))
        assertFalse(BonusState.dropped(listOf(knight), listOf(wizard, knight)))
        assertFalse(BonusState.dropped(emptyList(), listOf(knight)))
    }
}
