package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emoji.EmojiRegistry

class EmojiCatalogTest {

    @Test
    fun `catalog holds one entry per distinct glyph`() {
        val catalog = EmojiRegistry.catalog
        assertTrue(catalog.size > 500) { "expected the packed catalog, got ${catalog.size} entries" }
        assertEquals(catalog.size, catalog.map { it.glyph }.toSet().size)
        assertTrue(catalog.all { it.alias in it.aliases })
    }

    @Test
    fun `catalog is ordered by atlas index and stays inside the atlas`() {
        val indices = EmojiRegistry.catalog.map { EmojiRegistry.atlasIndex(it.glyph) }
        assertTrue(indices.all { it >= 0 }) { "every catalog glyph must live in the atlas" }
        assertEquals(indices.sorted(), indices)
        assertTrue(indices.last() < 32 * 24) { "atlas is 32x24 cells, saw index ${indices.last()}" }
    }

    @Test
    fun `atlasIndex rejects text that is not one of our glyphs`() {
        assertEquals(-1, EmojiRegistry.atlasIndex(""))
        assertEquals(-1, EmojiRegistry.atlasIndex("a"))
        assertEquals(-1, EmojiRegistry.atlasIndex("😀"))
    }

    @Test
    fun `search prefers prefix matches and honours the limit`() {
        val results = EmojiRegistry.search("smil")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().aliases.any { it.startsWith("smil") })
        assertTrue(results.any { it.glyph == EmojiRegistry.resolve("smile") })
        assertEquals(3, EmojiRegistry.search("s", 3).size)
    }

    @Test
    fun `search accepts a colon-wrapped query and an empty one`() {
        assertEquals(EmojiRegistry.search("smile"), EmojiRegistry.search(":smile:"))
        assertEquals(EmojiRegistry.catalog, EmojiRegistry.search(" "))
    }

    @Test
    fun `segments split shortcodes and unicode out of the surrounding text`() {
        val smile = EmojiRegistry.resolve("smile")
        assertNotNull(smile)
        val segments = EmojiRegistry.segments("hi :smile: there 😀")
        assertEquals(
            listOf("hi ", "<emoji>", " there ", "<emoji>"),
            segments.map {
                when (it) {
                    is EmojiRegistry.Segment.Text -> it.text
                    is EmojiRegistry.Segment.Emoji -> "<emoji>"
                }
            },
        )
        assertEquals(smile, (segments[1] as EmojiRegistry.Segment.Emoji).glyph)
    }

    @Test
    fun `segments leave plain text alone`() {
        val segments = EmojiRegistry.segments("no emoji here")
        assertEquals(1, segments.size)
        assertEquals("no emoji here", (segments.single() as EmojiRegistry.Segment.Text).text)
    }
}
