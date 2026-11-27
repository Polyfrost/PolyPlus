package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetBody
import org.polyfrost.polyplus.client.pets.PetBrain
import org.polyfrost.polyplus.client.pets.PetOwner
import org.polyfrost.polyplus.client.pets.PetSteer
import kotlin.math.sqrt

class PetCatchUpTest {
    private val leash = 3.0

    private fun distance(body: PetBody, owner: PetOwner) {
        val dx = owner.x - body.x
        val dz = owner.z - body.z
        body.distance = sqrt(dx * dx + dz * dz).toFloat()
    }

    private fun run(body: PetBody, owner: PetOwner, steer: PetSteer, ticks: Int, ownerStep: Double, start: Double = 0.0) {
        for (tick in 0 until ticks) {
            val now = start + tick * 0.05
            owner.x += ownerStep
            distance(body, owner)
            PetBrain.observe(body, owner, now, leash, false)
            steer.reset()
            PetBrain.catchUp(body, leash, false, owner, now, steer)
        }
    }

    @Test
    fun `a far pet that makes no progress keeps running instead of snapping`() {
        val body = PetBody(7L)
        val owner = PetOwner()
        owner.x = 100.0
        val steer = PetSteer()
        run(body, owner, steer, ticks = 400, ownerStep = 0.0)
        assertFalse(body.mind.snapNow)
        assertTrue(steer.moving)
    }

    @Test
    fun `an owner running away is chased, not teleported to`() {
        val body = PetBody(8L)
        val owner = PetOwner()
        owner.x = 40.0
        val steer = PetSteer()
        run(body, owner, steer, ticks = 200, ownerStep = 0.28)
        assertFalse(body.mind.snapNow)
        assertTrue(steer.moving)
    }

    @Test
    fun `an owner who teleports far away brings the pet along`() {
        val body = PetBody(9L)
        val owner = PetOwner()
        val steer = PetSteer()
        run(body, owner, steer, ticks = 5, ownerStep = 0.0)
        owner.x = 200.0
        distance(body, owner)
        PetBrain.observe(body, owner, 1.0, leash, false)
        PetBrain.catchUp(body, leash, false, owner, 1.0, steer)
        assertTrue(body.mind.snapNow)
    }

    @Test
    fun `a short owner jump within catch-up range does not snap`() {
        val body = PetBody(10L)
        val owner = PetOwner()
        val steer = PetSteer()
        run(body, owner, steer, ticks = 5, ownerStep = 0.0)
        owner.x = 10.0
        distance(body, owner)
        PetBrain.observe(body, owner, 1.0, leash, false)
        PetBrain.catchUp(body, leash, false, owner, 1.0, steer)
        assertFalse(body.mind.snapNow)
    }
}
