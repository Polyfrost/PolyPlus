package org.polyfrost.polyplus.test

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.DefinitionRouteGate
import org.polyfrost.polyplus.client.cosmetics.definitionRouteMissing
import org.polyfrost.polyplus.client.cosmetics.mergeGroupVariants
import org.polyfrost.polyplus.client.cosmetics.missingFromListing
import org.polyfrost.polyplus.client.cosmetics.requestedDefinitions
import org.polyfrost.polyplus.client.network.http.responses.CosmeticList

class DefinitionLookupTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val response = """
        {
          "cosmetics": [
            {
              "id": 1,
              "type": "cape",
              "name": "Hidden Cape",
              "allowed_slots": ["cape"],
              "variants": [
                { "id": 11, "name": "Red", "hash": "abc", "url": "https://example.invalid/red.png" },
                { "id": 12, "name": "Blue", "hash": "def", "url": "https://example.invalid/blue.png" }
              ]
            },
            {
              "id": 2,
              "type": "emote",
              "name": "Wave",
              "variants": [
                { "id": 21, "name": "Wave", "hash": "ghi", "url": "https://example.invalid/wave.zip" }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `only the asked-for cosmetic variants are taken from a lookup`() {
        val list = json.decodeFromString<CosmeticList>(response)
        assertEquals(listOf(11), requestedDefinitions(list, setOf(11, 21)).map { it.id })
    }

    @Test
    fun `a missing lookup route waits before it is tried again`() {
        val gate = DefinitionRouteGate(retryMs = 1_000)
        assertTrue(gate.available(0))
        gate.missing(0)
        assertFalse(gate.available(999))
        assertTrue(gate.available(1_000))
    }

    @Test
    fun `a missing lookup route says how long until it can be tried again`() {
        val gate = DefinitionRouteGate(retryMs = 1_000)
        assertEquals(0L, gate.waitMs(0))
        gate.missing(100)
        assertEquals(400L, gate.waitMs(700))
        assertEquals(0L, gate.waitMs(1_100))
    }

    @Test
    fun `only not-found and wrong-method answers mean the route is missing`() {
        assertTrue(definitionRouteMissing(404))
        assertTrue(definitionRouteMissing(405))
        assertFalse(definitionRouteMissing(429))
        assertFalse(definitionRouteMissing(500))
        assertFalse(definitionRouteMissing(null))
    }

    @Test
    fun `owned cosmetics left out of the public catalog are refreshed, local ones aren't`() {
        assertEquals(setOf(3), missingFromListing(owned = setOf(1, 2, 3, -4), listed = setOf(1, 2), local = setOf(-4)))
    }

    @Test
    fun `owned hidden variants join the public group in their owned order`() {
        assertEquals(listOf(11, 15, 12, 13), mergeGroupVariants(public = listOf(11, 12, 13), owned = listOf(11, 15, 12)))
        assertEquals(listOf(11, 9, 12), mergeGroupVariants(public = listOf(11, 12), owned = listOf(9, 12)))
        assertEquals(listOf(11, 12, 20, 21), mergeGroupVariants(public = listOf(11, 12), owned = listOf(20, 21)))
        assertEquals(listOf(11, 12), mergeGroupVariants(public = listOf(11, 12), owned = emptyList()))
    }
}
