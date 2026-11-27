package org.polyfrost.polyplus.test

import com.sun.management.ThreadMXBean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetGrid
import org.polyfrost.polyplus.client.pets.PetLongMap
import java.lang.management.ManagementFactory
import kotlin.random.Random

class PetAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private fun allocated(block: () -> Unit): Long {
        val before = threads.currentThreadAllocatedBytes
        block()
        return threads.currentThreadAllocatedBytes - before
    }

    @Test
    fun `a warm grid rebuilds and queries without allocating`() {
        val random = Random(7)
        val xs = DoubleArray(40) { random.nextDouble(-30.0, 30.0) }
        val zs = DoubleArray(40) { random.nextDouble(-30.0, 30.0) }
        val grid = PetGrid(2.0)
        val out = PetGrid.IntBag()
        fun frame() {
            grid.clear()
            for (i in xs.indices) grid.add(i, xs[i], zs[i])
            for (i in xs.indices) grid.move(i, xs[i] + 0.3, zs[i] - 0.3)
            for (i in xs.indices) grid.near(xs[i], zs[i], 4.0, out)
        }
        repeat(500) { frame() }
        assertEquals(0L, allocated { repeat(200) { frame() } })
    }

    @Test
    fun `long maps behave like hash maps`() {
        val random = Random(11)
        val map = PetLongMap<String>()
        val model = HashMap<Long, String>()
        repeat(5000) {
            val key = random.nextLong(-50, 50) * 0x1_0000_0001L
            when (random.nextInt(3)) {
                0 -> {
                    val value = random.nextInt().toString()
                    map.put(key, value)
                    model[key] = value
                }
                1 -> assertEquals(model.remove(key), map.remove(key))
                else -> assertEquals(model[key], map[key])
            }
            assertEquals(model.size, map.size)
        }
        for ((key, value) in model) assertEquals(value, map[key])
        map.clear()
        assertEquals(0, map.size)
        assertNull(map[0L])
    }

    @Test
    fun `a warm long map reads and writes without allocating`() {
        val map = PetLongMap<Any>()
        val value = Any()
        val keys = LongArray(30) { 0x7000_0000_0000_0000L + it * 977L }
        for (key in keys) map.put(key, value)
        repeat(500) { for (key in keys) map.put(key, map[key] ?: value) }
        assertEquals(0L, allocated { repeat(500) { for (key in keys) map.put(key, map[key] ?: value) } })
        assertTrue(map.size == keys.size)
    }
}
