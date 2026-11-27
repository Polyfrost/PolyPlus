package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetMarkClearance
import kotlin.math.abs

class PetMarkClearanceTest {
    private val sideBySide = doubleArrayOf(-0.5, 0.0, 0.4, 0.0, 0.5, 0.5, 0.0, 0.4, 0.0, 0.5)

    private fun disc(x: Double, z: Double, radius: Double, bottom: Double = 0.0, top: Double = 0.5) =
        doubleArrayOf(x, z, radius, bottom, top)

    private fun clearOfBoth(discs: DoubleArray, x: Double, z: Double, ax: Double, az: Double, bx: Double, bz: Double): Boolean {
        val count = discs.size / PetMarkClearance.STRIDE
        return PetMarkClearance.clears(discs, count, x, z, ax, 0.0, az) &&
            PetMarkClearance.clears(discs, count, x, z, bx, 0.0, bz)
    }

    @Test
    fun `owner hitbox counts as a box`() {
        assertFalse(PetMarkClearance.clears(disc(0.5, 0.0, 0.1), 1, 0.0, 0.0, 0.0, 0.0, 0.0))
        assertTrue(PetMarkClearance.clears(disc(0.75, 0.75, 0.1), 1, 0.0, 0.0, 0.0, 0.0, 0.0))
        assertTrue(PetMarkClearance.clears(disc(1.2, 0.0, 0.5), 1, 0.0, 0.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun `owners only block marks they overlap vertically`() {
        val low = disc(0.0, 0.0, 0.3, bottom = -3.0, top = -2.5)
        assertFalse(PetMarkClearance.clears(low, 1, 0.0, 0.0, 0.0, -4.0, 0.0))
        assertFalse(PetMarkClearance.clears(low, 1, 0.0, 0.0, 0.0, -2.9, 0.0))
        assertTrue(PetMarkClearance.clears(low, 1, 0.0, 0.0, 0.0, -2.5, 0.0))
        assertTrue(PetMarkClearance.clears(low, 1, 0.0, 0.0, 0.0, -4.8, 0.0))
    }

    @Test
    fun `occupancy ignores the clearance margin`() {
        val pet = disc(0.75, 0.0, 0.2)
        assertFalse(PetMarkClearance.clears(pet, 1, 0.0, 0.0, 0.0, 0.0, 0.0))
        assertTrue(PetMarkClearance.clears(pet, 1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        assertFalse(PetMarkClearance.clears(pet, 1, 0.0, 0.0, 0.4, 0.0, 0.0, 0.0))
    }

    @Test
    fun `a clear mark stays where it is`() {
        val out = DoubleArray(2)
        val found = PetMarkClearance.search(out, 4.0, 0.0, 0.0, 0.0, 8.0, 0.0, true) { x, z ->
            clearOfBoth(sideBySide, x, z, 0.0, 0.0, 8.0, 0.0)
        }
        assertTrue(found)
        assertEquals(4.0, out[0])
        assertEquals(0.0, out[1])
    }

    @Test
    fun `close owners push the mark sideways toward the pets`() {
        val out = DoubleArray(2)
        assertFalse(clearOfBoth(sideBySide, 1.25, 0.1, 0.0, 0.0, 2.5, 0.0))
        val found = PetMarkClearance.search(out, 1.25, 0.1, 0.0, 0.0, 2.5, 0.0, false) { x, z ->
            clearOfBoth(sideBySide, x, z, 0.0, 0.0, 2.5, 0.0)
        }
        assertTrue(found)
        assertEquals(1.25, out[0], 1.0e-9)
        assertTrue(out[1] > 0.1)
        assertTrue(clearOfBoth(sideBySide, out[0], out[1], 0.0, 0.0, 2.5, 0.0))
    }

    @Test
    fun `a blocked side falls back to the other side`() {
        val out = DoubleArray(2)
        val found = PetMarkClearance.search(out, 1.25, 0.1, 0.0, 0.0, 2.5, 0.0, true) { x, z ->
            z < 0.0 && clearOfBoth(sideBySide, x, z, 0.0, 0.0, 2.5, 0.0)
        }
        assertTrue(found)
        assertTrue(out[1] < 0.0)
    }

    @Test
    fun `a narrow corridor finds a spot along the owners' line`() {
        val out = DoubleArray(2)
        val inCorridor = { x: Double, z: Double -> abs(z) < 0.3 && clearOfBoth(sideBySide, x, z, 0.0, 0.0, 2.0, 0.0) }
        val found = PetMarkClearance.search(out, 1.0, 0.0, 0.0, 0.0, 2.0, 0.0, true) { x, z -> inCorridor(x, z) }
        assertTrue(found)
        assertTrue(inCorridor(out[0], out[1]))
        assertTrue(out[0] >= 3.5 || out[0] <= -1.5)
    }

    @Test
    fun `the search stays small and tries the nearest spots first`() {
        assertTrue(PetMarkClearance.probes <= 160)
        var last = 0.0
        PetMarkClearance.search(DoubleArray(2), 0.0, 0.0, -1.0, 0.0, 1.0, 0.0, true) { x, z ->
            val reach = x * x + z * z
            assertTrue(reach >= last - 1.0e-9)
            last = reach
            false
        }
    }

    @Test
    fun `a chase circle keeps both owners outside it`() {
        val out = DoubleArray(2)
        val circle = disc(0.0, 0.0, 2.9)
        val found = PetMarkClearance.search(out, 1.25, 0.0, 0.0, 0.0, 2.5, 0.0, true) { x, z ->
            clearOfBoth(circle, x, z, 0.0, 0.0, 2.5, 0.0)
        }
        assertTrue(found)
        assertTrue(out[1] > 0.0)
        assertTrue(clearOfBoth(circle, out[0], out[1], 0.0, 0.0, 2.5, 0.0))
    }

    @Test
    fun `no spot means no interaction`() {
        val out = DoubleArray(2)
        assertFalse(PetMarkClearance.search(out, 1.25, 0.0, 0.0, 0.0, 2.5, 0.0, true) { _, _ -> false })
    }
}
