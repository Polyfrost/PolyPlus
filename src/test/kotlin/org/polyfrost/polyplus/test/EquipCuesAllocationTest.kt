package org.polyfrost.polyplus.test

import com.sun.management.ThreadMXBean
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.EquipCues
import java.lang.management.ManagementFactory

class EquipCuesAllocationTest {
    @Test
    fun `drawing many wearers allocates nothing once warm`() {
        val cues = EquipCues(4)
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val id = Thread.currentThread().id
        repeat(WARMUP) { frame -> sweep(cues, frame) }
        val before = threads.getThreadAllocatedBytes(id)
        repeat(ROUNDS) { frame -> sweep(cues, WARMUP + frame) }
        val allocated = threads.getThreadAllocatedBytes(id) - before
        assertTrue(allocated <= ROUNDS, "allocated $allocated bytes over $ROUNDS sweeps of $WEARERS wearers")
    }

    private fun sweep(cues: EquipCues, frame: Int) {
        val now = frame * 0.01
        for (wearer in 0 until WEARERS) {
            val key = 100_000L + wearer * 7919L
            cues.seen(key, frame, now)
            cues.drawn(key, frame, 0, 3, 0, now)
            cues.drawn(key, frame, 1, 4, 0, now)
        }
    }

    private companion object {
        const val WEARERS = 24
        const val WARMUP = 2_000
        const val ROUNDS = 2_000
    }
}
