package org.polyfrost.polyplus.nameplate.engine.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache

class ByteLruCacheTest {
    private fun cache(maxBytes: Long): ByteLruCache<Int, String> = ByteLruCache(maxBytes) { it.length }

    private fun ByteLruCache<Int, String>.put(key: Int, value: String) {
        store(key, value)
    }

    private fun ByteLruCache<Int, String>.getOrPut(key: Int, create: () -> String): String = getOrPut(key, { it }, create)

    @Test
    fun evictsLeastRecentlyUsedFirst() {
        val cache = cache(6)
        cache.put(1, "aa")
        cache.put(2, "bb")
        cache.put(3, "cc")
        cache.put(4, "dd")
        assertNull(cache[1])
        assertEquals("bb", cache[2])
        assertEquals("cc", cache[3])
        assertEquals("dd", cache[4])
    }

    @Test
    fun getRefreshesRecency() {
        val cache = cache(6)
        cache.put(1, "aa")
        cache.put(2, "bb")
        cache.put(3, "cc")
        assertEquals("aa", cache[1])
        cache.put(4, "dd")
        assertEquals("aa", cache[1])
        assertNull(cache[2])
        assertEquals("cc", cache[3])
    }

    @Test
    fun getOrPutHitRefreshesRecency() {
        val cache = cache(6)
        cache.put(1, "aa")
        cache.put(2, "bb")
        cache.put(3, "cc")
        cache.getOrPut(1) { "zz" }
        cache.put(4, "dd")
        assertEquals("aa", cache[1])
        assertNull(cache[2])
    }

    @Test
    fun putRefreshesRecencyOfExistingKey() {
        val cache = cache(6)
        cache.put(1, "aa")
        cache.put(2, "bb")
        cache.put(3, "cc")
        cache.put(1, "AA")
        cache.put(4, "dd")
        assertEquals("AA", cache[1])
        assertNull(cache[2])
    }

    @Test
    fun oneLargeInsertEvictsSeveralEntries() {
        val cache = cache(8)
        cache.put(1, "aa")
        cache.put(2, "bb")
        cache.put(3, "cc")
        cache.put(4, "dd")
        cache.put(5, "123456")
        assertEquals(8, cache.bytes)
        assertNull(cache[1])
        assertNull(cache[2])
        assertNull(cache[3])
        assertEquals("dd", cache[4])
        assertEquals("123456", cache[5])
    }

    @Test
    fun accountsBytesOnPutAndReplace() {
        val cache = cache(100)
        assertEquals(0, cache.bytes)
        cache.put(1, "aaa")
        cache.put(2, "bb")
        assertEquals(5, cache.bytes)
        cache.put(1, "a")
        assertEquals(3, cache.bytes)
        cache.put(2, "bbbbbb")
        assertEquals(7, cache.bytes)
    }

    @Test
    fun replacingWithLargerValueEvictsOthers() {
        val cache = cache(6)
        cache.put(1, "aa")
        cache.put(2, "bb")
        cache.put(3, "cc")
        cache.put(1, "aaaaaa")
        assertEquals(6, cache.bytes)
        assertEquals("aaaaaa", cache[1])
        assertNull(cache[2])
        assertNull(cache[3])
    }

    @Test
    fun valueAtExactlyTheBudgetIsStored() {
        val cache = cache(4)
        cache.put(1, "abcd")
        assertEquals("abcd", cache[1])
        assertEquals(4, cache.bytes)
    }

    @Test
    fun oversizedValueIsNotStored() {
        val cache = cache(4)
        cache.put(1, "aa")
        assertNull(cache.store(2, "abcde"))
        assertNull(cache[2])
        assertEquals("aa", cache[1])
        assertEquals(2, cache.bytes)
    }

    @Test
    fun oversizedReplacementDropsTheOldValue() {
        val cache = cache(4)
        cache.put(1, "aa")
        cache.put(1, "abcde")
        assertNull(cache[1])
        assertEquals(0, cache.bytes)
    }

    @Test
    fun getOrPutReturnsOversizedValueWithoutStoringIt() {
        val cache = cache(4)
        var calls = 0
        val first = cache.getOrPut(1) { calls++; "abcde" }
        val second = cache.getOrPut(1) { calls++; "abcde" }
        assertEquals("abcde", first)
        assertEquals("abcde", second)
        assertEquals(2, calls)
        assertEquals(0, cache.bytes)
    }

    @Test
    fun getOrPutCreatesOncePerMiss() {
        val cache = cache(100)
        var calls = 0
        val first = cache.getOrPut(1) { calls++; "aa" }
        val second = cache.getOrPut(1) { calls++; "bb" }
        val third = cache.getOrPut(2) { calls++; "cc" }
        assertEquals(2, calls)
        assertSame(first, second)
        assertEquals("cc", third)
        assertEquals("aa", cache[1])
    }

    @Test
    fun getOrPutStoresUnderTheKeptKey() {
        val cache = cache(100)
        cache.getOrPut(1, { it + 10 }) { "aa" }
        assertNull(cache[1])
        assertEquals("aa", cache[11])
    }

    @Test
    fun getOrPutCreatesAgainAfterEviction() {
        val cache = cache(2)
        var calls = 0
        cache.getOrPut(1) { calls++; "aa" }
        cache.getOrPut(2) { calls++; "bb" }
        cache.getOrPut(1) { calls++; "aa" }
        assertEquals(3, calls)
    }

    @Test
    fun evictedNodesCannotBeRecalled() {
        val cache = cache(4)
        val node = assertNotNull(cache.store(1, "aa"))
        assertEquals("aa", cache.recall(node))
        cache.put(2, "bb")
        cache.put(3, "cc")
        assertNull(cache.recall(node))
    }

    @Test
    fun recallRefreshesRecency() {
        val cache = cache(4)
        val node = assertNotNull(cache.store(1, "aa"))
        cache.put(2, "bb")
        cache.recall(node)
        cache.put(3, "cc")
        assertEquals("aa", cache[1])
        assertNull(cache[2])
    }

    @Test
    fun zeroBudgetStoresNothing() {
        val cache = cache(0)
        assertEquals("aa", cache.getOrPut(1) { "aa" })
        assertEquals(0, cache.bytes)
        assertNull(cache[1])
    }

    @Test
    fun rejectsNegativeBudget() {
        assertFailsWith<IllegalArgumentException> { cache(-1) }
    }

    @Test
    fun rejectsNegativeValueSize() {
        val cache = ByteLruCache<Int, Int>(10) { it }
        assertFailsWith<IllegalArgumentException> { cache.store(1, -1) }
        assertEquals(0, cache.bytes)
    }

    @Test
    fun matchesReferenceModelOverMixedSequence() {
        val cache = cache(40)
        val model = Model(40)
        var seed = 12345
        repeat(5000) { step ->
            seed = seed * 1103515245 + 12345
            val roll = (seed ushr 8) and 0xFFFF
            val key = roll % 12
            val value = "v".repeat((roll / 12) % 14 + 1) + step
            when (roll % 4) {
                0, 1 -> assertEquals(model.get(key), cache[key])
                2 -> assertEquals(model.getOrPut(key, value), cache.getOrPut(key) { value })
                else -> {
                    cache.put(key, value)
                    model.put(key, value)
                }
            }

            assertEquals(model.bytes, cache.bytes, "bytes at step $step")
        }
    }

    private class Model(private val maxBytes: Long) {
        val entries = ArrayList<Pair<Int, String>>()

        val bytes: Long
            get() = entries.sumOf { it.second.length.toLong() }

        fun get(key: Int): String? {
            val index = entries.indexOfFirst { it.first == key }
            if (index < 0) {
                return null
            }

            val entry = entries.removeAt(index)
            entries.add(entry)
            return entry.second
        }

        fun getOrPut(key: Int, value: String): String {
            val found = get(key)
            if (found != null) {
                return found
            }

            put(key, value)
            return value
        }

        fun put(key: Int, value: String) {
            val index = entries.indexOfFirst { it.first == key }
            if (index >= 0) {
                entries.removeAt(index)
            }
            if (value.length > maxBytes) {
                return
            }

            entries.add(key to value)
            while (bytes > maxBytes) {
                entries.removeAt(0)
            }
        }
    }
}
