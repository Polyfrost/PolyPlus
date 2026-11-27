package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.RetiredBuffers
import org.polyfrost.polyplus.client.cosmetics.render.SoundReleases

class RetiredBuffersTest {
    @Test
    fun discardsOnlyAfterTheGracePeriod() {
        val retired = RetiredBuffers(30_000)
        val discarded = mutableListOf<String>()
        val owner = Any()
        retired.retire(owner, { discarded.add("a") }, 1_000)
        retired.retire(owner, { discarded.add("b") }, 5_000)
        retired.expire(30_999)
        assertEquals(emptyList<String>(), discarded)
        retired.expire(31_000)
        assertEquals(listOf("a"), discarded)
        retired.expire(40_000)
        assertEquals(listOf("a", "b"), discarded)
        assertEquals(0, retired.size)
    }

    @Test
    fun keepsAFailedDiscardForAnotherTry() {
        val retired = RetiredBuffers(30_000, 1_000)
        val owner = Any()
        var attempts = 0
        retired.retire(owner, { ++attempts >= 3 }, 0)
        retired.expire(30_000)
        assertEquals(1, attempts)
        assertEquals(1, retired.size)
        retired.expire(30_500)
        assertEquals(1, attempts)
        retired.expire(31_000)
        assertEquals(2, attempts)
        retired.expire(32_000)
        assertEquals(3, attempts)
        assertEquals(0, retired.size)
    }

    @Test
    fun discardNowRunsOnTheNextExpiry() {
        val retired = RetiredBuffers(30_000)
        val discarded = mutableListOf<String>()
        retired.retire(Any(), { discarded.add("old") }, 1_000)
        retired.discardNow(Any(), { discarded.add("now") }, 2_000)
        retired.expire(2_000)
        assertEquals(listOf("now"), discarded)
        assertEquals(1, retired.size)
    }

    @Test
    fun onlyTheLatestReleaseOfAKeyRetiresIt() {
        val releases = SoundReleases()
        val first = releases.released("chime")
        val second = releases.released("chime")
        assertTrue(releases.pending("chime"))
        assertFalse(releases.settle("chime", first))
        assertTrue(releases.pending("chime"))
        assertTrue(releases.settle("chime", second))
        assertFalse(releases.pending("chime"))
        assertFalse(releases.settle("chime", second))
    }

    @Test
    fun flushDiscardsOnlyThatOwnersBuffersImmediately() {
        val retired = RetiredBuffers(30_000)
        val discarded = mutableListOf<String>()
        val first = Any()
        val second = Any()
        retired.retire(first, { discarded.add("a") }, 1_000)
        retired.retire(second, { discarded.add("b") }, 1_000)
        retired.retire(first, { discarded.add("c") }, 2_000)
        retired.flush(first)
        assertEquals(listOf("a", "c"), discarded)
        assertEquals(1, retired.size)
        retired.expire(31_000)
        assertEquals(listOf("a", "c", "b"), discarded)
    }
}
