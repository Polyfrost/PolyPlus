package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetPaintCache
import org.polyfrost.polyplus.client.pets.PetPaintEntry
import org.polyfrost.polyplus.client.pets.PetPaintSlot

class PetPaintCacheTest {
    private class Paint(val key: Long) : PetPaintEntry {
        override var drawnFrame = Long.MIN_VALUE
    }

    private val small = 64L * 64 * 4
    private val huge = 1024L * 1024 * 4

    private fun PetPaintCache<Paint>.paint(key: Long, frame: Long, released: MutableList<Long>): Paint? {
        get(key)?.let {
            it.drawnFrame = frame
            return it
        }
        if (!admit(frame) { released += it.key }) return null
        return Paint(key).also { put(key, it, frame) }
    }

    @Test
    fun `every visible palette gets its own paint while it fits the byte budget`() {
        val cache = PetPaintCache<Paint>(small)
        val released = ArrayList<Long>()
        for (frame in 0L until 5L) {
            for (key in 0L until 40L) assertNotNull(cache.paint(key, frame, released))
        }
        assertEquals(40, cache.size)
        assertTrue(released.isEmpty())
    }

    @Test
    fun `palettes no longer drawn make room for new ones`() {
        val cache = PetPaintCache<Paint>(small)
        val released = ArrayList<Long>()
        for (key in 0L until 40L) cache.paint(key, 0L, released)
        cache.paint(100L, 5L, released)
        assertEquals(cache.kept, cache.size)
        assertEquals((0L until 25L).toList(), released.sorted())
    }

    @Test
    fun `a palette drawn this frame or last frame is never released`() {
        val cache = PetPaintCache<Paint>(small)
        val released = ArrayList<Long>()
        for (key in 0L until 16L) cache.paint(key, 1L, released)
        cache.paint(0L, 2L, released)
        cache.paint(1L, 1L, released)
        cache.paint(100L, 2L, released)
        assertTrue(released.isEmpty())
        cache.paint(101L, 3L, released)
        assertFalse(0L in released)
        assertFalse(100L in released)
        assertTrue(released.isNotEmpty())
    }

    @Test
    fun `large textures stay within the byte budget`() {
        val cache = PetPaintCache<Paint>(huge)
        val released = ArrayList<Long>()
        assertEquals(4, cache.budget)
        for (key in 0L until 4L) assertNotNull(cache.paint(key, 0L, released))
        assertNull(cache.paint(4L, 0L, released))
        assertNull(cache.paint(4L, 1L, released))
        for (key in 1L until 4L) cache.paint(key, 2L, released)
        assertNotNull(cache.paint(4L, 2L, released))
        assertEquals(listOf(0L), released)
        assertEquals(4, cache.size)
    }

    @Test
    fun `a replaced pet drops the paint it was holding`() {
        val slot = PetPaintSlot<String>()
        slot.hold("old", 3, 1, emptyMap(), null)
        assertFalse(slot.drop("new"))
        assertEquals("old", slot.asset)
        assertTrue(slot.drop("old"))
        assertNull(slot.asset)
        assertNull(slot.paint)
        assertEquals(-1, slot.generation)
    }
}
