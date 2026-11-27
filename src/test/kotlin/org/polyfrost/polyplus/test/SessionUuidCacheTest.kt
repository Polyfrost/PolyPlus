package org.polyfrost.polyplus.test

import com.sun.management.ThreadMXBean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.utils.SessionUuidCache
import java.lang.management.ManagementFactory
import java.util.UUID

class SessionUuidCacheTest {
    @Test
    fun `a miss returns null and a stored value comes back`() {
        val cache = SessionUuidCache()
        val id = UUID(1L, 2L)
        assertNull(cache.lookup("a"))
        cache.store("a", id)
        assertSame(id, cache.lookup("a"))
        assertNull(cache.lookup("b"))
    }

    @Test
    fun `a stored failure rethrows the same error for the same input`() {
        val cache = SessionUuidCache()
        val error = IllegalArgumentException("bad")
        cache.storeFailure("offline", error)
        assertSame(error, assertThrows(IllegalArgumentException::class.java) { cache.lookup("offline") })
        assertNull(cache.lookup("other"))
        cache.store("other", UUID(3L, 4L))
        assertEquals(UUID(3L, 4L), cache.lookup("other"))
    }

    @Test
    fun `repeated failing lookups allocate nothing once warm`() {
        val cache = SessionUuidCache()
        cache.storeFailure("offline", IllegalArgumentException("bad"))
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val id = Thread.currentThread().id
        repeat(WARMUP) { probe(cache) }
        val before = threads.getThreadAllocatedBytes(id)
        repeat(ROUNDS) { probe(cache) }
        val allocated = threads.getThreadAllocatedBytes(id) - before
        assertTrue(allocated <= ROUNDS, "allocated $allocated bytes over $ROUNDS lookups")
    }

    private fun probe(cache: SessionUuidCache) {
        try {
            cache.lookup("offline")
        } catch (_: IllegalArgumentException) {
        }
    }

    private companion object {
        const val WARMUP = 20_000
        const val ROUNDS = 10_000
    }
}
