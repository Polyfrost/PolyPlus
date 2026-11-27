package org.polyfrost.polyplus.test

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket

class SnapshotEmoteDefinitionTest {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val early = "11111111-1111-1111-1111-111111111111"
    private val late = "22222222-2222-2222-2222-222222222222"
    private val partner = "33333333-3333-3333-3333-333333333333"

    private fun snapshot(activeDefinitions: String): ClientboundPacket.SubscriptionSnapshot =
        json.decodeFromString<ClientboundPacket>(
            """
            {
              "type": "SubscriptionSnapshot",
              "equipped": {},
              "active_emotes": { "$early": 7, "$late": 7 },
              "emote_definitions": {
                "7": { "id": 7, "name": "Wave", "url": "https://objects.example/7-new.zip", "hash": "new" }
              }
              $activeDefinitions
            }
            """.trimIndent(),
        ) as ClientboundPacket.SubscriptionSnapshot

    @Test
    fun `a player's own revision wins over the shared definition`() {
        val packet = snapshot(
            """
            , "active_emote_definitions": {
              "$early": { "id": 7, "name": "Wave", "url": "https://objects.example/7-old.zip", "hash": "old" }
            }
            """,
        )
        assertEquals("old", packet.emoteDefinitionFor(listOf(early), 7)?.hash)
        assertEquals("new", packet.emoteDefinitionFor(listOf(late), 7)?.hash)
    }

    @Test
    fun `a duo partner shares the revision the session started with`() {
        val packet = snapshot(
            """
            , "active_emote_definitions": {
              "$early": { "id": 7, "name": "Wave", "hash": "old" }
            }
            """,
        )
        assertEquals("old", packet.emoteDefinitionFor(listOf(partner, partner, early), 7)?.hash)
    }

    @Test
    fun `a revision for another emote is ignored`() {
        val packet = snapshot(
            """
            , "active_emote_definitions": {
              "$early": { "id": 8, "name": "Bow", "hash": "bow" }
            }
            """,
        )
        assertEquals("new", packet.emoteDefinitionFor(listOf(early), 7)?.hash)
        assertNull(packet.emoteDefinitionFor(listOf(early), 9))
    }

    @Test
    fun `an older backend without per-play revisions still resolves`() {
        val packet = snapshot("")
        assertEquals("new", packet.emoteDefinitionFor(listOf(early), 7)?.hash)
    }
}
