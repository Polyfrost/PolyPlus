package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetBody
import org.polyfrost.polyplus.client.pets.PetBrain
import org.polyfrost.polyplus.client.pets.PetDirective
import org.polyfrost.polyplus.client.pets.PetSteer

class PetGreetTest {
    private fun directive(x: Double, y: Double, z: Double) = PetDirective().apply {
        markX = x
        markY = y
        markZ = z
        faceYaw = 90f
    }

    @Test
    fun `a pet on its mark stays and faces its partner`() {
        val body = PetBody(3L)
        val steer = PetSteer()
        assertTrue(PetBrain.holdMark(body, directive(0.2, 0.0, 0.0), false, steer))
        assertFalse(steer.moving)
        assertEquals(90f, steer.faceYaw)
    }

    @Test
    fun `a walking pet follows a mark that moved with the shoulder pet's owner`() {
        val body = PetBody(4L)
        val steer = PetSteer()
        assertFalse(PetBrain.holdMark(body, directive(3.0, 0.0, 1.0), false, steer))
        assertTrue(steer.moving)
        assertEquals(3.0, steer.x)
        assertEquals(1.0, steer.z)
    }

    @Test
    fun `a flying pet follows a mark that rose with the owner`() {
        val body = PetBody(5L)
        val steer = PetSteer()
        assertFalse(PetBrain.holdMark(body, directive(0.0, 2.0, 0.0), true, steer))
        assertTrue(steer.moving)
        assertEquals(2.0, steer.y)
    }
}
