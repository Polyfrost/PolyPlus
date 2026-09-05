package org.polyfrost.polyplus.test

import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.featured.FeaturedServerCatalogCodec
import org.polyfrost.polyplus.client.featured.FeaturedServerCachePolicy
import org.polyfrost.polyplus.client.featured.FeaturedServerColors
import org.polyfrost.polyplus.client.featured.FeaturedServersSnapshot
import org.polyfrost.polyplus.client.featured.OutlineStyle
import org.polyfrost.polyplus.client.featured.normalizeServerAddress

class FeaturedServerCatalogTest {
    @Test
    fun `decodes solid rainbow and future outline styles independently`() {
        val result = FeaturedServerCatalogCodec.decode(
            """
            {
              "schema_version": 1,
              "servers": [
                {"id":"solid","name":"Solid","address":"solid.example","outline_color":"#7C3AED"},
                {"id":"rainbow","name":"Rainbow","address":"rainbow.example","outline_color":"rainbow"},
                {"id":"none","name":"None","address":"none.example","outline_color":"none"},
                {"id":"future","name":"Future","address":"future.example","outline_color":{"type":"gradient","colors":["#f00","#00f"]}},
                {"id":"broken","outline_color":"#xyz"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("solid", "rainbow", "none", "future"), result.servers.map { it.id })
        assertEquals(0xFF7C3AED.toInt(), (result.servers[0].outlineStyle as OutlineStyle.Solid).argb)
        assertEquals(OutlineStyle.Rainbow, result.servers[1].outlineStyle)
        assertEquals(OutlineStyle.None, result.servers[2].outlineStyle)
        assertInstanceOf(OutlineStyle.Unsupported::class.java, result.servers[3].outlineStyle)
        assertInstanceOf(JsonObject::class.java, (result.servers[3].outlineStyle as OutlineStyle.Unsupported).raw)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `bad campaign becomes sponsored instead of removing server`() {
        val result = FeaturedServerCatalogCodec.decode(
            """{"schema_version":1,"servers":[{
              "id":"server","name":"Server","address":"play.example","outline_color":"#ffffff",
              "featured":{"campaign_id":"campaign","starts_at":"bad","ends_at":"also-bad","title":"Title","description":"Body","cta_label":"Play"}
            }]}""",
        )
        assertEquals(1, result.servers.size)
        assertEquals(null, result.servers.single().featured)
        assertTrue(result.warnings.single().contains("featured ignored"))
    }

    @Test
    fun `bad optional image does not remove campaign`() {
        val result = FeaturedServerCatalogCodec.decode(
            """{"schema_version":1,"servers":[{
              "id":"server","name":"Server","address":"play.example","outline_color":"#ffffff",
              "featured":{"campaign_id":"campaign","starts_at":"2026-01-01T00:00:00Z","ends_at":"2027-01-01T00:00:00Z","title":"Title","description":"Body","cta_label":"Play","image_url":"http://example.invalid/banner.png"}
            }]}""",
        )
        assertEquals("campaign", result.servers.single().featured?.campaignId)
        assertEquals(null, result.servers.single().featured?.imageUrl)
    }

    @Test
    fun `selection partitions every visible server once`() {
        val decoded = FeaturedServerCatalogCodec.decode(
            """{"schema_version":1,"servers":[
              {"id":"a","name":"A","address":"a.example","outline_color":"#ffffff","featured":{"campaign_id":"one","starts_at":"2026-01-01T00:00:00Z","ends_at":"2027-01-01T00:00:00Z","title":"A","description":"A","cta_label":"Play"}},
              {"id":"b","name":"B","address":"b.example","outline_color":"#ffffff"},
              {"id":"c","name":"C","address":"c.example","outline_color":"#ffffff","featured":{"campaign_id":"three","starts_at":"2026-01-01T00:00:00Z","ends_at":"2027-01-01T00:00:00Z","title":"C","description":"C","cta_label":"Play"}}
            ]}""",
        )
        val now = java.time.Instant.parse("2026-09-05T00:00:00Z").toEpochMilli()
        val snapshot = FeaturedServersSnapshot(
            decoded.servers,
            mainMenuDismissedCampaignIds = setOf("one"),
            multiplayerDismissedCampaignIds = setOf("three"),
            expiresAtMillis = now + 1_000,
            revision = 1,
        )
        assertEquals(listOf("c"), snapshot.mainMenuFeaturedServers(now).map { it.id })
        assertEquals(listOf("a"), snapshot.featuredServers(now).map { it.id })
        assertEquals(listOf("b", "c"), snapshot.sponsoredServers(now).map { it.id })
        assertTrue(snapshot.visibleServers(now + 1_000).isEmpty())
    }

    @Test
    fun `rainbow is deterministic distinct and wraps at four seconds`() {
        val start = FeaturedServerColors.rainbowAt(0f, 0L)
        assertEquals(start, FeaturedServerColors.rainbowAt(0f, 4_000L))
        assertFalse(start == FeaturedServerColors.rainbowAt(0.25f, 0L))
        assertFalse(start == FeaturedServerColors.rainbowAt(0f, 1_000L))
        assertEquals(0x00000000, FeaturedServerColors.colorAt(OutlineStyle.None, 0f, 0L))
        assertEquals(FeaturedServerColors.FALLBACK_ARGB, FeaturedServerColors.colorAt(OutlineStyle.Unsupported(kotlinx.serialization.json.JsonNull), 0f, 0L))
    }

    @Test
    fun `normalizes default ports and host casing`() {
        assertEquals("play.example.net:25565", normalizeServerAddress("PLAY.Example.Net."))
        assertEquals("play.example.net:25565", normalizeServerAddress("play.example.net:25565"))
        assertEquals("[2001:db8::1]:25565", normalizeServerAddress("[2001:DB8::1]"))
    }

    @Test
    fun `cache and refresh boundaries are exact`() {
        val fetchedAt = 1_000L
        assertTrue(FeaturedServerCachePolicy.isFresh(fetchedAt, fetchedAt))
        assertFalse(FeaturedServerCachePolicy.isFresh(fetchedAt, FeaturedServerCachePolicy.expiresAt(fetchedAt)))
        assertTrue(FeaturedServerCachePolicy.shouldRefresh(0L, fetchedAt, false))
        assertFalse(FeaturedServerCachePolicy.shouldRefresh(fetchedAt, fetchedAt + 1_000L, false))
        assertTrue(FeaturedServerCachePolicy.shouldRefresh(fetchedAt, fetchedAt + FeaturedServerCachePolicy.REFRESH_INTERVAL_MILLIS, false))
        assertTrue(FeaturedServerCachePolicy.shouldRefresh(fetchedAt, fetchedAt + 1L, true))
    }
}
