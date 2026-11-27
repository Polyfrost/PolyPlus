package org.polyfrost.polyplus.test

import com.sun.management.ThreadMXBean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.nameplate.engine.LongObjectMap
import java.lang.management.ManagementFactory
import java.util.Random

class LongObjectMapTest {
    @Test
    fun `behaves like a hash map under random inserts and removals`() {
        val map = LongObjectMap<String>(2)
        val expected = HashMap<Long, String>()
        val random = Random(7)
        repeat(20_000) { step ->
            val key = (random.nextInt(400) - 200).toLong() * 0x1_0000_0001L
            when (random.nextInt(3)) {
                0 -> {
                    val value = "v$step"
                    map[key] = value
                    expected[key] = value
                }
                1 -> assertEquals(expected.remove(key), map.remove(key))
                else -> assertEquals(expected[key], map[key])
            }
            assertEquals(expected.size, map.size)
        }
        val seen = HashMap<Long, String>()
        for (i in 0 until map.capacity) {
            val value = map.valueAt(i) ?: continue
            seen[map.keyAt(i)] = value
        }
        assertEquals(expected, seen)
    }

    @Test
    fun `clear empties the map`() {
        val map = LongObjectMap<String>()
        for (k in 0L until 50L) map[k] = "x"
        map.clear()
        assertEquals(0, map.size)
        assertNull(map[3L])
        map[3L] = "y"
        assertEquals("y", map[3L])
    }

    @Test
    fun `lookups and replacements do not allocate`() {
        val map = LongObjectMap<String>()
        val value = "slot"
        for (k in 0L until 64L) map[k * 100_003L] = value
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val id = Thread.currentThread().threadId()
        fun spin(): Int {
            var hits = 0
            for (round in 0 until 2_000) {
                for (k in 0L until 64L) {
                    if (map[k * 100_003L] != null) hits++
                    map[k * 100_003L] = value
                }
            }
            return hits
        }
        spin()
        val before = bean.getThreadAllocatedBytes(id)
        val hits = spin()
        val after = bean.getThreadAllocatedBytes(id)
        assertEquals(2_000 * 64, hits)
        assertTrue(after - before < 4_096, "allocated ${after - before} bytes")
    }
}
