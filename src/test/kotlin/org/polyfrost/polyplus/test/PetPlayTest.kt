package org.polyfrost.polyplus.test

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.pets.PetPlay
import org.polyfrost.polyplus.client.pets.PetPlayPermissions
import java.util.UUID

class PetPlayTest {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val local = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val alex = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val blair = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val casey = UUID.fromString("00000000-0000-0000-0000-00000000000c")

    private fun change(body: String): ClientboundPacket.PlayerPetPreferencesChanged =
        json.decodeFromString<ClientboundPacket>(
            """{ "type": "PlayerPetPreferencesChanged", "player": "$alex" $body }""",
        ) as ClientboundPacket.PlayerPetPreferencesChanged

    private fun PetPlayPermissions.allows(a: UUID, b: UUID, localPlay: PetPlay = PetPlay.EVERYONE, localFriends: Set<UUID> = emptySet()) =
        allows(a, b, local, localPlay) { it in localFriends }

    @Test
    fun `a preference change reads the new setting and friend list`() {
        val packet = change(""", "interactions": true, "pet_play": "friends", "friends": ["$blair", "$local"]""")
        assertEquals(PetPlay.FRIENDS, packet.play)
        assertEquals(listOf(blair.toString(), local.toString()), packet.friends)
    }

    @Test
    fun `a preference change from an older server still reads the on and off switch`() {
        assertEquals(PetPlay.OFF, change(""", "interactions": false""").play)
        assertEquals(PetPlay.EVERYONE, change(""", "interactions": true""").play)
        assertEquals(PetPlay.EVERYONE, change("").play)
    }

    @Test
    fun `the named setting wins over the switch`() {
        assertEquals(PetPlay.OFF, change(""", "interactions": false, "pet_play": "off", "friends": []""").play)
        assertEquals(PetPlay.EVERYONE, change(""", "interactions": true, "pet_play": "everyone", "friends": []""").play)
        assertEquals(PetPlay.FRIENDS, change(""", "interactions": true, "pet_play": "friends", "friends": []""").play)
    }

    @Test
    fun `the snapshot reads friend lists per player`() {
        val packet = json.decodeFromString<ClientboundPacket>(
            """
            {
              "type": "SubscriptionSnapshot",
              "equipped": {},
              "active_emotes": {},
              "pet_interactions_friends": { "$alex": ["$blair"] }
            }
            """.trimIndent(),
        ) as ClientboundPacket.SubscriptionSnapshot
        assertEquals(mapOf(alex.toString() to listOf(blair.toString())), packet.petInteractionsFriends)
    }

    @Test
    fun `the setting is sent by name`() {
        val encoded = json.encodeToString(ServerboundPacket.serializer(), ServerboundPacket.SetPetPreferences(PetPlay.FRIENDS))
        assertEquals("""{"type":"SetPetPreferences","interactions":"friends"}""", encoded.replace(Regex("\\s"), ""))
    }

    @Test
    fun `everyone plays with everyone`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair), emptyList(), emptyMap())
        assertTrue(permissions.allows(alex, blair))
        assertTrue(permissions.allows(alex, casey))
    }

    @Test
    fun `an owner who turned play off blocks every pair`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair), listOf(alex), emptyMap())
        assertFalse(permissions.allows(alex, blair))
        assertFalse(permissions.allows(blair, alex))
        assertTrue(permissions.allows(blair, local))
        assertFalse(permissions.allows(blair, local, localPlay = PetPlay.OFF))
        assertFalse(permissions.plays(alex, local, PetPlay.EVERYONE))
        assertFalse(permissions.plays(local, local, PetPlay.OFF))
        assertTrue(permissions.plays(blair, local, PetPlay.EVERYONE))
    }

    @Test
    fun `a friends only owner plays only with listed friends`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair, casey), emptyList(), mapOf(alex to listOf(blair)))
        assertTrue(permissions.allows(alex, blair))
        assertTrue(permissions.allows(blair, alex))
        assertFalse(permissions.allows(alex, casey))
        assertFalse(permissions.allows(casey, alex))
        assertFalse(permissions.allows(alex, local))
        assertTrue(permissions.plays(alex, local, PetPlay.EVERYONE))
    }

    @Test
    fun `two friends only owners must list each other`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair), emptyList(), mapOf(alex to listOf(blair), blair to emptyList()))
        assertFalse(permissions.allows(alex, blair))
        permissions.applySnapshot(emptyList(), emptyList(), mapOf(blair to listOf(alex)))
        assertTrue(permissions.allows(alex, blair))
    }

    @Test
    fun `the local owner uses the local friends list`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(local, alex, blair), emptyList(), mapOf(local to listOf(alex)))
        assertTrue(permissions.allows(local, blair, localPlay = PetPlay.EVERYONE))
        assertFalse(permissions.allows(local, alex, localPlay = PetPlay.FRIENDS, localFriends = setOf(blair)))
        assertTrue(permissions.allows(local, blair, localPlay = PetPlay.FRIENDS, localFriends = setOf(blair)))
        assertTrue(permissions.allows(blair, local, localPlay = PetPlay.FRIENDS, localFriends = setOf(blair)))
    }

    @Test
    fun `a friends only owner listing the local player allows the pair`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex), emptyList(), mapOf(alex to listOf(local)))
        assertTrue(permissions.allows(alex, local))
        assertTrue(permissions.allows(local, alex))
    }

    @Test
    fun `snapshot entries add friends without dropping earlier ones`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair), emptyList(), mapOf(alex to listOf(blair)))
        permissions.applySnapshot(listOf(casey), emptyList(), mapOf(alex to listOf(casey)))
        assertTrue(permissions.allows(alex, blair))
        assertTrue(permissions.allows(alex, casey))
    }

    @Test
    fun `a newly snapshotted player without an entry plays with everyone`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair), listOf(alex), emptyMap())
        permissions.applySnapshot(listOf(alex), emptyList(), emptyMap())
        assertTrue(permissions.allows(alex, blair))
    }

    @Test
    fun `a preference change replaces the setting and friend set`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair, casey), emptyList(), mapOf(alex to listOf(blair)))
        permissions.applyChange(alex, PetPlay.FRIENDS, listOf(casey))
        assertFalse(permissions.allows(alex, blair))
        assertTrue(permissions.allows(alex, casey))
        permissions.applyChange(alex, PetPlay.EVERYONE, listOf(casey))
        assertTrue(permissions.allows(alex, blair))
        permissions.applyChange(alex, PetPlay.FRIENDS, emptyList())
        assertFalse(permissions.allows(alex, casey))
        permissions.applyChange(alex, PetPlay.OFF, emptyList())
        assertFalse(permissions.plays(alex, local, PetPlay.EVERYONE))
    }

    @Test
    fun `forgetting a player drops them from every friend set`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair), emptyList(), mapOf(alex to listOf(blair)))
        permissions.forget(listOf(blair))
        assertFalse(permissions.allows(alex, blair))
        permissions.applySnapshot(listOf(blair), emptyList(), mapOf(alex to listOf(blair)))
        assertTrue(permissions.allows(alex, blair))
    }

    @Test
    fun `clearing drops every setting`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair), listOf(blair), mapOf(alex to emptyList()))
        permissions.clear()
        assertTrue(permissions.allows(alex, blair))
    }

    @Test
    fun `every viewer reaches the same answer for either order`() {
        val permissions = PetPlayPermissions()
        permissions.applySnapshot(listOf(alex, blair, casey), listOf(casey), mapOf(alex to listOf(blair), blair to listOf(casey)))
        val owners = listOf(local, alex, blair, casey)
        for (play in PetPlay.entries) {
            for (a in owners) for (b in owners) {
                assertEquals(
                    permissions.allows(a, b, local, play) { it == alex },
                    permissions.allows(b, a, local, play) { it == alex },
                )
            }
        }
    }
}
