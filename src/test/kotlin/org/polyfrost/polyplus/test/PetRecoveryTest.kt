package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.pets.PetArchetype
import org.polyfrost.polyplus.client.pets.PetBody
import org.polyfrost.polyplus.client.pets.PetBrain
import org.polyfrost.polyplus.client.pets.PetInteractions
import org.polyfrost.polyplus.client.pets.PetMind
import org.polyfrost.polyplus.client.pets.PetOwner
import org.polyfrost.polyplus.client.pets.PetPairRules
import org.polyfrost.polyplus.client.pets.PetSimulation
import org.polyfrost.polyplus.client.pets.PetStall
import org.polyfrost.polyplus.client.pets.PetSteer
import kotlin.math.sqrt

class PetRecoveryTest {
    private class Candidate(
        val name: String,
        val x: Double,
        val cooling: Boolean = false,
        val startable: Boolean = true,
        val compatible: Boolean = true,
    )

    private object Rules : PetPairRules<Candidate> {
        override fun x(pet: Candidate): Double = pet.x

        override fun gapSq(p: Candidate, q: Candidate): Double = (p.x - q.x) * (p.x - q.x)

        override fun compatible(p: Candidate, q: Candidate): Boolean = q.compatible

        override fun cooling(p: Candidate, q: Candidate): Boolean = q.cooling

        override fun startable(p: Candidate, q: Candidate): Boolean = q.startable

        override fun before(q: Candidate, best: Candidate): Boolean = q.name < best.name
    }

    private class Remembering : PetPairRules<Candidate> by Rules {
        val refusals = HashSet<String>()

        override fun refused(p: Candidate, q: Candidate): Boolean = q.name in refusals

        override fun refuse(p: Candidate, q: Candidate) {
            refusals += q.name
        }
    }

    @Test
    fun `a pet pressing against a new block gives up on its path`() {
        val stall = PetStall()
        assertFalse(stall.stuck(3, 2.0))
        repeat(PetStall.STUCK_TICKS - 1) { assertFalse(stall.stuck(3, 2.0)) }
        assertTrue(stall.stuck(3, 2.0))
        assertFalse(stall.stuck(3, 2.0))
    }

    @Test
    fun `a pet that keeps closing in is never treated as stuck`() {
        val stall = PetStall()
        var distance = 6.0
        repeat(200) {
            assertFalse(stall.stuck(1, distance))
            distance -= 0.02
        }
    }

    @Test
    fun `reaching a waypoint restarts the stuck timer`() {
        val stall = PetStall()
        repeat(PetStall.STUCK_TICKS - 1) { assertFalse(stall.stuck(1, 2.0)) }
        assertFalse(stall.stuck(2, 2.0))
        repeat(PetStall.STUCK_TICKS - 1) { assertFalse(stall.stuck(2, 2.0)) }
        assertTrue(stall.stuck(2, 2.0))
    }

    @Test
    fun `a pet left in unloaded terrain respawns after a second`() {
        val body = PetBody(3L)
        assertFalse(PetSimulation.strandedFor(body, true, 10.0))
        assertFalse(PetSimulation.strandedFor(body, true, 10.5))
        assertTrue(PetSimulation.strandedFor(body, true, 11.0))
    }

    @Test
    fun `a pet whose terrain loads again stays where it is`() {
        val body = PetBody(4L)
        assertFalse(PetSimulation.strandedFor(body, true, 10.0))
        assertFalse(PetSimulation.strandedFor(body, false, 10.6))
        assertFalse(PetSimulation.strandedFor(body, true, 10.7))
        assertFalse(PetSimulation.strandedFor(body, true, 11.5))
        assertTrue(PetSimulation.strandedFor(body, true, 11.8))
    }

    @Test
    fun `an owner falling through a frame stall is not taken for a teleport`() {
        val body = PetBody(5L)
        val owner = PetOwner()
        val steer = PetSteer()
        val leash = 3.0
        owner.x = 40.0
        owner.sampleTick = 100L
        settle(body, owner, leash, 0.0)
        owner.y -= 30.0
        owner.sampleTick += 10L
        settle(body, owner, leash, 0.5)
        PetBrain.catchUp(body, leash, false, owner, 0.5, steer)
        assertFalse(body.mind.snapNow)
        assertTrue(steer.moving)
    }

    @Test
    fun `an owner moving far in one tick is still a teleport`() {
        val body = PetBody(6L)
        val owner = PetOwner()
        val steer = PetSteer()
        val leash = 3.0
        owner.x = 40.0
        owner.sampleTick = 100L
        settle(body, owner, leash, 0.0)
        owner.y -= 30.0
        owner.sampleTick += 1L
        settle(body, owner, leash, 0.05)
        PetBrain.catchUp(body, leash, false, owner, 0.05, steer)
        assertTrue(body.mind.snapNow)
    }

    @Test
    fun `a shoulder pet's owner who sleeps or glides is busy`() {
        val mind = PetMind(1L)
        val owner = PetOwner()
        assertTrue(PetBrain.ownerFree(PetArchetype.Shoulder, owner, mind, 0.0))
        owner.signals = flags(PlayerSignals.IS_SLEEPING)
        assertFalse(PetBrain.ownerFree(PetArchetype.Shoulder, owner, mind, 0.0))
        owner.signals = flags(PlayerSignals.IS_GLIDING)
        assertFalse(PetBrain.ownerFree(PetArchetype.Shoulder, owner, mind, 0.0))
        assertFalse(PetBrain.ownerFree(PetArchetype.Walking, owner, mind, 0.0))
    }

    @Test
    fun `a nearer partner that can't start anything loses to a farther one that can`() {
        val order = listOf(Candidate("p", 0.0), Candidate("nap", 1.0, startable = false), Candidate("greet", 3.0))
        assertSame(order[2], PetInteractions.partner(order, 0, Rules))
    }

    @Test
    fun `no partner is picked when none can start`() {
        val order = listOf(Candidate("p", 0.0), Candidate("nap", 1.0, startable = false))
        assertNull(PetInteractions.partner(order, 0, Rules))
    }

    @Test
    fun `cooling pairs don't use up the candidate cap`() {
        val cooling = (1..8).map { Candidate("c$it", it * 0.5, cooling = true) }
        val forward = listOf(Candidate("p", 0.0)) + cooling + Candidate("free", 5.0)
        assertEquals("free", PetInteractions.partner(forward, 0, Rules)?.name)
        val backward = listOf(Candidate("free", -5.0)) + cooling.map { Candidate(it.name, -it.x, cooling = true) }.reversed() +
            Candidate("p", 0.0)
        assertEquals("free", PetInteractions.partner(backward, backward.size - 1, Rules)?.name)
    }

    @Test
    fun `nearby pets that can't start anything don't hide one that can`() {
        val napping = (1..8).map { Candidate("nap$it", it * 0.5, startable = false) }
        val forward = listOf(Candidate("p", 0.0)) + napping + Candidate("greet", 5.0)
        assertEquals("greet", PetInteractions.partner(forward, 0, Rules)?.name)
        val backward = listOf(Candidate("greet", -5.0)) + napping.map { Candidate(it.name, -it.x, startable = false) }.reversed() +
            Candidate("p", 0.0)
        assertEquals("greet", PetInteractions.partner(backward, backward.size - 1, Rules)?.name)
    }

    @Test
    fun `a crowd of pets that can't start is worked through over later scans`() {
        val napping = (1..30).map { Candidate("nap$it", it * 0.1, startable = false) }
        val order = listOf(Candidate("p", 0.0)) + napping + Candidate("greet", 4.0)
        val rules = Remembering()
        var found: Candidate? = null
        var scans = 0
        while (found == null && scans < 4) {
            found = PetInteractions.partner(order, 0, rules)
            scans++
        }
        assertEquals("greet", found?.name)
        assertTrue(scans > 1)
    }

    private fun flags(vararg signals: Int): PlayerSignalRecord {
        val record = PlayerSignalRecord()
        for (signal in signals) record.values[signal] = 1f
        return record
    }

    private fun settle(body: PetBody, owner: PetOwner, leash: Double, now: Double) {
        val dx = owner.x - body.x
        val dy = owner.y - body.y
        val dz = owner.z - body.z
        body.distance = sqrt(dx * dx + dy * dy + dz * dz).toFloat()
        PetBrain.observe(body, owner, now, leash, false)
    }
}
