package org.polyfrost.polyplus.test

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.EmoteRevision
import org.polyfrost.polyplus.client.network.http.responses.EmoteDefinition

class EmoteRevisionPinTest {
    private val emoteId = 987_654
    private val first = EmoteDefinition(emoteId, url = "https://example.invalid/1.zip", hash = "a".repeat(64))
    private val second = EmoteDefinition(emoteId, url = "https://example.invalid/2.zip", hash = "b".repeat(64))
    private val holderA = Any()
    private val holderB = Any()

    @AfterEach
    fun release() {
        CosmeticCatalog.releaseEmotePin(holderA)
        CosmeticCatalog.releaseEmotePin(holderB)
    }

    @Test
    fun `a newer revision doesn't replace an older play`() {
        val a = CosmeticCatalog.pinEmote(holderA, emoteId, first)
        val b = CosmeticCatalog.pinEmote(holderB, emoteId, second)

        assertEquals(EmoteRevision(emoteId, first.hash), a)
        assertEquals(EmoteRevision(emoteId, second.hash), b)
        assertEquals(first.hash, CosmeticCatalog.getEmoteDefinition(a!!)?.hash)
        assertEquals(second.hash, CosmeticCatalog.getEmoteDefinition(b!!)?.hash)
        assertEquals(second.hash, CosmeticCatalog.getEmoteDefinition(emoteId)?.hash)
    }

    @Test
    fun `releasing the newest pin falls back to the remaining revision`() {
        val a = CosmeticCatalog.pinEmote(holderA, emoteId, first)!!
        CosmeticCatalog.pinEmote(holderB, emoteId, second)
        CosmeticCatalog.releaseEmotePin(holderB)

        assertEquals(first.hash, CosmeticCatalog.getEmoteDefinition(emoteId)?.hash)
        assertEquals(first.hash, CosmeticCatalog.getEmoteDefinition(a)?.hash)
        assertNull(CosmeticCatalog.getEmoteDefinition(EmoteRevision(emoteId, second.hash)))
    }

    @Test
    fun `a repeat play without a definition keeps its revision`() {
        val a = CosmeticCatalog.pinEmote(holderA, emoteId, first)
        CosmeticCatalog.pinEmote(holderB, emoteId, second)

        assertEquals(a, CosmeticCatalog.pinEmote(holderA, emoteId, null))
    }

    @Test
    fun `an id lookup returns the catalog revision over an older pin`() {
        val listedId = emoteId + 1
        val pinned = EmoteDefinition(listedId, url = "https://example.invalid/old.zip", hash = "c".repeat(64))
        val listed = EmoteDefinition(listedId, url = "https://example.invalid/new.zip", hash = "d".repeat(64))
        CosmeticCatalog.addLocal(listed.asCosmeticDefinition(), null)
        val a = CosmeticCatalog.pinEmote(holderA, listedId, pinned)!!

        assertEquals(listed.hash, CosmeticCatalog.getEmoteDefinition(listedId)?.hash)
        assertEquals(listed.hash, CosmeticCatalog.getDefinition(listedId)?.hash)
        assertEquals(pinned.hash, CosmeticCatalog.getEmoteDefinition(a)?.hash)
        assertEquals(EmoteRevision(listedId, listed.hash), CosmeticCatalog.pinEmote(holderB, listedId, null))
    }

    @Test
    fun `revisions get distinct texture tags`() {
        assertNotEquals(EmoteRevision(emoteId, first.hash).tag, EmoteRevision(emoteId, second.hash).tag)
    }
}
