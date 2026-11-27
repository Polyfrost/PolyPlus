package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetGrid
import kotlin.math.abs
import kotlin.random.Random

class PetGridTest {
    @Test
    fun `near finds every point within reach in index order`() {
        val random = Random(42)
        val xs = DoubleArray(300) { random.nextDouble(-40.0, 40.0) }
        val zs = DoubleArray(300) { random.nextDouble(-40.0, 40.0) }
        val grid = PetGrid(2.0)
        for (i in xs.indices) grid.add(i, xs[i], zs[i])
        val out = PetGrid.IntBag()
        repeat(200) {
            val x = random.nextDouble(-42.0, 42.0)
            val z = random.nextDouble(-42.0, 42.0)
            val reach = random.nextDouble(0.1, 3.0)
            grid.near(x, z, reach, out)
            val found = (0 until out.size).map { out[it] }
            assertEquals(found.sorted(), found)
            assertEquals(found.toSet().size, found.size)
            for (i in xs.indices) {
                if (abs(xs[i] - x) < reach && abs(zs[i] - z) < reach) assertTrue(i in found, "point $i")
            }
        }
    }

    @Test
    fun `a moved point is found at its new cell only`() {
        val grid = PetGrid(2.0)
        grid.add(0, 0.5, 0.5)
        grid.add(1, 10.5, 10.5)
        grid.move(0, 20.5, -20.5)
        val out = PetGrid.IntBag()
        grid.near(0.5, 0.5, 1.0, out)
        assertEquals(0, out.size)
        grid.near(20.5, -20.5, 1.0, out)
        assertEquals(listOf(0), (0 until out.size).map { out[it] })
    }

    @Test
    fun `cleared grids forget their points`() {
        val grid = PetGrid(2.0)
        grid.add(0, 1.0, 1.0)
        grid.clear()
        grid.move(0, 1.0, 1.0)
        val out = PetGrid.IntBag()
        grid.near(1.0, 1.0, 5.0, out)
        assertEquals(0, out.size)
        grid.add(3, -1.0, -1.0)
        grid.near(1.0, 1.0, 5.0, out)
        assertEquals(listOf(3), (0 until out.size).map { out[it] })
    }
}
