package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetCollision
import org.polyfrost.polyplus.client.pets.PetFootprint

class PetFlightTest {
    private class World(vararg lava: Triple<Int, Int, Int>) : PetFootprint.Probe {
        private val lava = lava.toSet()

        override fun at(x: Int, y: Int, z: Int): Int =
            if (Triple(x, y, z) in lava) PetCollision.DANGER else PetCollision.SAFE
    }

    @Test
    fun `a flying pet moving toward lava sees it before it enters`() {
        val world = World(Triple(2, 64, 0))
        assertEquals(PetCollision.SAFE, PetFootprint.sweep(0.5, 64.2, 0.5, 0.5, 0.0, 0.0, 0.3, 0.6, world))
        assertEquals(PetCollision.DANGER, PetFootprint.sweep(0.5, 64.2, 0.5, 1.4, 0.0, 0.0, 0.3, 0.6, world))
    }

    @Test
    fun `a flying pet diving over lava sees the lava below`() {
        val world = World(Triple(0, 63, 0))
        assertEquals(PetCollision.SAFE, PetFootprint.hazard(0.5, 64.2, 0.5, 0.3, 0.6, world))
        assertEquals(PetCollision.SAFE, PetFootprint.sweep(0.5, 64.2, 0.5, 0.4, 0.0, 0.0, 0.3, 0.6, world))
        assertEquals(PetCollision.DANGER, PetFootprint.sweep(0.5, 64.2, 0.5, 0.0, -0.3, 0.0, 0.3, 0.6, world))
    }

    @Test
    fun `the whole body height is checked along the route`() {
        val world = World(Triple(1, 65, 0))
        assertEquals(PetCollision.SAFE, PetFootprint.sweep(0.5, 64.0, 0.5, 0.6, 0.0, 0.0, 0.3, 0.6, world))
        assertEquals(PetCollision.DANGER, PetFootprint.sweep(0.5, 64.0, 0.5, 0.6, 0.0, 0.0, 0.3, 1.2, world))
    }

    @Test
    fun `a still pet checks the same blocks as before`() {
        val world = World(Triple(0, 63, 0), Triple(1, 64, 0))
        for (y in listOf(63.0, 63.96, 64.0, 64.04, 64.5)) {
            for (x in listOf(0.2, 0.5, 0.75)) {
                assertEquals(
                    PetFootprint.hazard(x, y, 0.5, 0.3, 0.9, world),
                    PetFootprint.sweep(x, y, 0.5, 0.0, 0.0, 0.0, 0.3, 0.9, world),
                )
            }
        }
    }
}
