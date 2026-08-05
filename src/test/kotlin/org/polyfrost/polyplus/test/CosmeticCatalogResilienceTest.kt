package org.polyfrost.polyplus.test

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.network.http.responses.CosmeticList

class CosmeticCatalogResilienceTest {
    private companion object {
        val JSON = Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }

        val VALID = """
            {
              "cosmetics": [
                {
                  "id": 1,
                  "type": "cape",
                  "name": "Test Cape",
                  "allowed_slots": ["cape"],
                  "variants": [
                    { "id": 11, "name": "Red", "hash": "abc", "url": "https://example.invalid/red.png" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val UNKNOWN_FIELDS = """
            {
              "cosmetics": [
                { "id": 1, "type": "cape", "name": "Test Cape", "variants": [], "released_at": "tomorrow" }
              ],
              "page": 1
            }
        """.trimIndent()

        val MISSING_REQUIRED = """
            {"cosmetics": [{"id": 1, "type": "cape", "variants": [{"id": 11, "name": "Red"}]}]}
        """.trimIndent()

        val WRONG_SHAPE = """{"cosmetics": "none"}"""

        val NOT_JSON = "<html><body>502 Bad Gateway</body></html>"
    }

    @Test
    fun `decodes a well-formed catalog`() {
        val list = JSON.decodeFromString<CosmeticList>(VALID)
        assertEquals(1, list.contents.size)
        assertEquals(listOf(11), list.contents.single().variants.map { it.id })
    }

    @Test
    fun `tolerates fields the client does not know`() {
        val list = JSON.decodeFromString<CosmeticList>(UNKNOWN_FIELDS)
        assertEquals(1, list.contents.size)
    }

    @Test
    fun `a malformed catalog fails as a catchable exception, never an error`() {
        for (payload in listOf(MISSING_REQUIRED, WRONG_SHAPE, NOT_JSON)) {
            val result = runCatching { JSON.decodeFromString<CosmeticList>(payload) }
            assertTrue(result.isFailure, "expected $payload to fail")
            val error = result.exceptionOrNull()!!
            assertTrue(error is Exception, "expected an Exception, got ${error.javaClass.name}")
            assertFalse(CosmeticCatalog.isRuntimeMismatch(error))
        }
    }

    @Test
    fun `recognises the serialization runtime mismatch, however deeply wrapped`() {
        val linkage = AbstractMethodError(
            "'kotlinx.serialization.KSerializer[] " +
                "kotlinx.serialization.internal.GeneratedSerializer.typeParametersSerializers()'",
        )
        assertTrue(CosmeticCatalog.isRuntimeMismatch(linkage))
        assertTrue(CosmeticCatalog.isRuntimeMismatch(IllegalStateException("decode failed", linkage)))
        assertFalse(CosmeticCatalog.isRuntimeMismatch(IllegalStateException("decode failed")))
    }
}
