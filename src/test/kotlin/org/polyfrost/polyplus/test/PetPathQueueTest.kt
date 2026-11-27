package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetCollision
import org.polyfrost.polyplus.client.pets.PetFootprint
import org.polyfrost.polyplus.client.pets.PetPathCredit
import org.polyfrost.polyplus.client.pets.PetPathQueue

class PetPathQueueTest {
    @Test
    fun `a crowd shares one budget per tick however many pets catch up`() {
        val queue = PetPathQueue(32, 8)
        var used = 0
        val stepper = PetPathQueue.Stepper { _, turn ->
            used += turn.budget
            turn.taken = turn.budget
            turn.work = turn.budget
            true
        }
        assertTrue(queue.serve(100L, 20, stepper))
        assertFalse(queue.serve(100L, 20, stepper))
        assertFalse(queue.serve(100L, 20, stepper))
        assertEquals(32, used)
        assertTrue(queue.serve(101L, 20, stepper))
        assertEquals(64, used)
    }

    @Test
    fun `searches past the first few pets get their turn`() {
        val queue = PetPathQueue(32, 8)
        val served = IntArray(10)
        val stepper = PetPathQueue.Stepper { index, turn ->
            served[index]++
            turn.taken = turn.budget
            true
        }
        for (tick in 0L until 3L) queue.serve(tick, served.size, stepper)
        assertTrue(served.all { it >= 1 }, served.joinToString())
        assertTrue(served.all { it <= 2 }, served.joinToString())
    }

    @Test
    fun `pets that aren't searching don't take a turn`() {
        val queue = PetPathQueue(32, 8)
        val served = ArrayList<Int>()
        val stepper = PetPathQueue.Stepper { index, turn ->
            if (index % 2 == 0) {
                false
            } else {
                served += index
                turn.taken = turn.budget
                true
            }
        }
        queue.serve(0L, 12, stepper)
        assertEquals(listOf(1, 3, 5, 7), served)
        served.clear()
        queue.serve(1L, 12, stepper)
        assertEquals(listOf(9, 11, 1, 3), served)
    }

    private class Crowd(pets: Int, private val cost: Int) {
        val credits = Array(pets) { PetPathCredit() }
        val nodes = IntArray(pets)
        var tickWork = 0
        val stepper = PetPathQueue.Stepper { index, turn ->
            val credit = credits[index]
            credit.begin(cost, turn.budget, turn.headroom)
            while (credit.affordable()) {
                credit.charge()
                nodes[index]++
            }
            turn.taken = credit.end(true)
            turn.work = credit.work
            tickWork += credit.work
            true
        }
    }

    private fun run(queue: PetPathQueue, crowd: Crowd, ticks: Int, pool: Int): Int {
        var total = 0
        for (tick in 0L until ticks.toLong()) {
            crowd.tickWork = 0
            queue.serve(tick, crowd.nodes.size, crowd.stepper)
            assertTrue(crowd.tickWork <= pool, "tick $tick spent ${crowd.tickWork}")
            total += crowd.tickWork
        }
        return total
    }

    @Test
    fun `two big near searches never spend past the pool in one tick`() {
        val crowd = Crowd(2, 21)
        val total = run(PetPathQueue(64, 32), crowd, 40, 64)
        assertTrue(total <= 64 * 40, "total $total")
        assertTrue(crowd.nodes.all { it >= 40 }, crowd.nodes.joinToString())
    }

    @Test
    fun `a big far search saves up for a node instead of overspending`() {
        val crowd = Crowd(1, 21)
        val total = run(PetPathQueue(32, 8), crowd, 30, 32)
        assertTrue(total <= 8 * 30, "total $total")
        assertEquals(11, crowd.nodes[0])
    }

    @Test
    fun `a crowd of big far searches stays inside the pool and everyone moves`() {
        val crowd = Crowd(10, 21)
        val total = run(PetPathQueue(32, 8), crowd, 100, 32)
        assertTrue(total <= 32 * 100, "total $total")
        assertTrue(crowd.nodes.all { it >= 8 }, crowd.nodes.joinToString())
    }

    @Test
    fun `a finished search hands its unspent budget back`() {
        val credit = PetPathCredit()
        credit.begin(1, 32, 64)
        repeat(5) {
            assertTrue(credit.affordable())
            credit.charge()
        }
        assertEquals(5, credit.end(false))
        assertEquals(0, credit.credit)
    }

    @Test
    fun `a node too big for the pool left this tick waits with its credit`() {
        val credit = PetPathCredit()
        credit.begin(21, 32, 10)
        assertFalse(credit.affordable())
        assertEquals(21, credit.end(true))
        assertEquals(21, credit.credit)
        credit.begin(21, 8, 64)
        assertTrue(credit.affordable())
        credit.charge()
        assertEquals(8, credit.end(true))
        assertEquals(8, credit.credit)
    }

    private fun lavaAt(lx: Int, ly: Int, lz: Int) = PetFootprint.Probe { x, y, z ->
        if (x == lx && y == ly && z == lz) PetCollision.DANGER else PetCollision.SAFE
    }

    @Test
    fun `lava under the edge of a big pet's box counts as danger`() {
        val probe = lavaAt(1, 63, 0)
        assertEquals(PetCollision.SAFE, PetFootprint.hazard(0.5, 64.0, 0.5, 0.0, 0.6, probe))
        assertEquals(PetCollision.SAFE, PetFootprint.hazard(0.5, 64.0, 0.5, 0.5, 0.6, probe))
        assertEquals(PetCollision.DANGER, PetFootprint.hazard(0.5, 64.0, 0.5, 1.0, 0.6, probe))
    }

    @Test
    fun `water beside a big pet's centre counts as water`() {
        val probe = PetFootprint.Probe { x, y, z -> if (x == -1 && y == 64 && z == 1) PetCollision.WATER else PetCollision.SAFE }
        assertEquals(PetCollision.SAFE, PetFootprint.hazard(0.5, 64.0, 0.5, 0.25, 0.6, probe))
        assertEquals(PetCollision.WATER, PetFootprint.hazard(0.5, 64.0, 0.5, 1.0, 0.6, probe))
    }

    @Test
    fun `lava at a tall pet's body height counts as danger`() {
        val probe = lavaAt(0, 65, 0)
        assertEquals(PetCollision.SAFE, PetFootprint.hazard(0.5, 64.0, 0.5, 0.3, 0.6, probe))
        assertEquals(PetCollision.SAFE, PetFootprint.hazard(0.5, 64.0, 0.5, 0.3, 1.0, probe))
        assertEquals(PetCollision.DANGER, PetFootprint.hazard(0.5, 64.0, 0.5, 0.3, 1.2, probe))
        assertEquals(PetCollision.DANGER, PetFootprint.hazard(0.5, 64.0, 0.5, 0.3, 2.0, probe))
        assertEquals(PetCollision.SAFE, PetFootprint.hazard(0.5, 64.0, 0.5, 0.3, 2.0, lavaAt(0, 66, 0)))
    }
}
