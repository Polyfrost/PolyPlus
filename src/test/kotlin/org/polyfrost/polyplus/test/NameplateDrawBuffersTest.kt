package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.nameplate.material.NameplateDrawBuffers

class NameplateDrawBuffersTest {
    private val none = NameplateDrawBuffers.NONE
    private val color0 = 0x8CE0
    private val color1 = 0x8CE1
    private val color2 = 0x8CE2

    private fun fill(cache: NameplateDrawBuffers, framebuffer: Int, vararg buffers: Int): Int {
        val slot = cache.claim(framebuffer)
        System.arraycopy(buffers, 0, cache.buffers, cache.offset(slot), buffers.size)
        cache.commit(slot, buffers.size)
        return slot
    }

    @Test
    fun `a framebuffer writing to several buffers narrows to its first one`() {
        val cache = NameplateDrawBuffers(4, 8)
        val slot = fill(cache, 7, color0, color2, color1, none)
        assertEquals(color0, cache.target(slot))
        assertEquals(3, cache.length(slot))
    }

    @Test
    fun `a framebuffer writing to one buffer is left alone`() {
        val cache = NameplateDrawBuffers(4, 8)
        val slot = fill(cache, 7, color0, none, none, none)
        assertEquals(none, cache.target(slot))
    }

    @Test
    fun `an unused first slot narrows to the first buffer in use`() {
        val cache = NameplateDrawBuffers(4, 8)
        val slot = fill(cache, 7, none, color2, color1)
        assertEquals(color2, cache.target(slot))
        assertEquals(3, cache.length(slot))
    }

    @Test
    fun `the restore list keeps gaps up to the last buffer in use`() {
        val cache = NameplateDrawBuffers(4, 8)
        val slot = fill(cache, 7, color0, none, color2, none, none)
        assertEquals(3, cache.length(slot))
        assertEquals(color0, cache.buffers[cache.offset(slot)])
        assertEquals(none, cache.buffers[cache.offset(slot) + 1])
        assertEquals(color2, cache.buffers[cache.offset(slot) + 2])
    }

    @Test
    fun `a framebuffer is found until the frame resets`() {
        val cache = NameplateDrawBuffers(4, 8)
        val slot = fill(cache, 7, color0, color1)
        assertEquals(slot, cache.find(7))
        assertEquals(-1, cache.find(8))
        cache.reset()
        assertEquals(-1, cache.find(7))
    }

    @Test
    fun `a full cache reuses its oldest slot`() {
        val cache = NameplateDrawBuffers(2, 8)
        fill(cache, 1, color0, color1)
        fill(cache, 2, color0, color1)
        fill(cache, 3, color0, color1)
        assertEquals(-1, cache.find(1))
        assertTrue(cache.find(2) >= 0)
        assertTrue(cache.find(3) >= 0)
    }

    @Test
    fun `more buffers than the slot holds are clamped`() {
        val cache = NameplateDrawBuffers(1, 2)
        val slot = cache.claim(5)
        cache.buffers[0] = color0
        cache.buffers[1] = color1
        cache.commit(slot, 6)
        assertEquals(2, cache.length(slot))
        assertEquals(color0, cache.target(slot))
    }
}
