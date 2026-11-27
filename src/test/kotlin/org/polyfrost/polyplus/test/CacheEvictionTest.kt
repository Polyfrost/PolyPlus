package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.CacheEviction

class CacheEvictionTest {
    private val names = listOf(
        "emote-5-aaaa",
        "emote-5-bbbb",
        "nameplate-3-cccc",
        "bundle-dddd",
        "bundle-id-9",
        "bonus-eeee",
        "mojang",
        "hashes.json",
    )

    @Test
    fun `only outdated downloads are removed`() {
        val keep = setOf("emote-5-bbbb", "bundle-dddd", "bonus-eeee")
        assertEquals(listOf("emote-5-aaaa", "nameplate-3-cccc", "bundle-id-9"), CacheEviction.obsolete(names, keep) { false })
    }

    @Test
    fun `recently written downloads stay while they may still be loading`() {
        assertEquals(listOf("bundle-id-9"), CacheEviction.obsolete(names, emptySet()) { it != "bundle-id-9" })
    }

    @Test
    fun `downloads stored under the old per-id keys are removed after the grace period`() {
        val old = listOf("cosmetic-12", "cosmetic-340", "cosmetic-notes", "mojang")
        assertTrue(CacheEviction.managed("cosmetic-12"))
        assertEquals(listOf("cosmetic-12", "cosmetic-340"), CacheEviction.obsolete(old, emptySet()) { false })
        assertEquals(listOf("cosmetic-340"), CacheEviction.obsolete(old, emptySet()) { it == "cosmetic-12" })
    }

    @Test
    fun `files the cache does not own are never removed`() {
        assertEquals(emptyList<String>(), CacheEviction.obsolete(listOf("mojang", "hashes.json", "skins"), emptySet()) { false })
    }
}
