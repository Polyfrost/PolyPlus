package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emotes.EmotePlays
import org.polyfrost.polyplus.client.emotes.endsDevPlayback
import org.polyfrost.polyplus.client.emotes.snapshotActive
import org.polyfrost.polyplus.client.emotes.snapshotSessions
import org.polyfrost.polyplus.client.emotes.snapshotSkips
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.network.http.responses.SharedEmote
import java.util.UUID

class EmotePlaysTest {
    private class Backend(var sends: Boolean = true) {
        val stops = ArrayList<Pair<Int, Long?>>()
        val drops = ArrayList<Pair<Int, Long>>()

        fun plays() = EmotePlays(
            stop = { emoteId, startTime ->
                if (sends) stops += emoteId to startTime
                sends
            },
            drop = { emoteId, startTime -> drops += emoteId to startTime },
        )
    }

    @Test
    fun `a cancelled play is stopped when it starts and hides its reply and broadcast`() {
        val backend = Backend()
        val plays = backend.plays()
        plays.sent(1L, 7)
        assertTrue(plays.cancel())
        assertTrue(backend.stops.isEmpty())
        assertTrue(plays.skips(7, 1_000L, 1L))
        assertEquals(listOf(7 to 1_000L), backend.stops)
        assertEquals(listOf(7 to 1_000L), backend.drops)
        assertTrue(plays.skips(7, 1_000L, null))
        assertFalse(plays.skips(7, 1_000L, null))
        plays.sent(2L, 7)
        assertFalse(plays.skips(7, 1_200L, 2L))
        assertFalse(plays.skips(7, 1_200L, null))
        assertEquals(1, backend.stops.size)
    }

    @Test
    fun `a rejected cancelled play sends no stop and doesn't hide the next play`() {
        val backend = Backend()
        val plays = backend.plays()
        plays.sent(1L, 7)
        assertTrue(plays.cancel())
        plays.failed(1L)
        plays.sent(2L, 7)
        assertFalse(plays.skips(7, 2_000L, 2L))
        assertFalse(plays.skips(7, 2_000L, null))
        assertTrue(backend.stops.isEmpty())
    }

    @Test
    fun `a stop during the local load stops that play and cancels applying it`() {
        val backend = Backend()
        val plays = backend.plays()
        plays.sent(1L, 7)
        assertFalse(plays.skips(7, 1_000L, 1L))
        assertTrue(plays.cancel())
        assertEquals(listOf(7 to 1_000L), backend.stops)
        assertEquals(listOf(7 to 1_000L), backend.drops)
        assertTrue(plays.skips(7, 1_000L, null))
        assertFalse(plays.cancel())
        assertEquals(1, backend.stops.size)
    }

    @Test
    fun `an applied play is left to the controller`() {
        val backend = Backend()
        val plays = backend.plays()
        plays.sent(1L, 7)
        assertFalse(plays.skips(7, 1_000L, 1L))
        plays.applied(7, 1_000L)
        assertFalse(plays.cancel())
        assertTrue(backend.stops.isEmpty())
    }

    @Test
    fun `a play applied from its broadcast before the reply is left to the controller`() {
        val backend = Backend()
        val plays = backend.plays()
        plays.sent(1L, 7)
        assertFalse(plays.skips(7, 1_000L, null))
        plays.applied(7, 1_000L)
        assertFalse(plays.skips(7, 1_000L, 1L))
        assertFalse(plays.cancel())
        assertTrue(backend.stops.isEmpty())
    }

    @Test
    fun `only the newest started play waits to be applied`() {
        val backend = Backend()
        val plays = backend.plays()
        plays.sent(1L, 3)
        plays.sent(2L, 4)
        assertFalse(plays.skips(3, 10L, 1L))
        assertFalse(plays.skips(4, 11L, 2L))
        assertTrue(plays.cancel())
        assertEquals(listOf(4 to 11L), backend.stops)
    }

    @Test
    fun `a stop that fails to send leaves the play shown and stoppable`() {
        val backend = Backend(sends = false)
        val plays = backend.plays()
        plays.sent(1L, 7)
        assertTrue(plays.cancel())
        assertFalse(plays.skips(7, 1_000L, 1L))
        assertFalse(plays.skips(7, 1_000L, null))
        assertTrue(backend.drops.isEmpty())
        backend.sends = true
        assertTrue(plays.cancel())
        assertEquals(listOf(7 to 1_000L), backend.stops)
    }

    @Test
    fun `cancelled plays stop whichever one actually started`() {
        val backend = Backend()
        val plays = backend.plays()
        plays.sent(1L, 3)
        plays.sent(2L, 4)
        assertTrue(plays.cancel())
        assertTrue(plays.skips(3, 10L, 1L))
        plays.failed(2L)
        assertEquals(listOf(3 to 10L), backend.stops)
        assertEquals(listOf(3 to 10L), backend.drops)
        assertTrue(plays.skips(3, 10L, null))
    }

    @Test
    fun `every cancelled play that started gets its own stop`() {
        val backend = Backend()
        val plays = backend.plays()
        plays.sent(1L, 3)
        plays.sent(2L, 4)
        assertTrue(plays.cancel())
        assertTrue(plays.skips(3, 10L, 1L))
        assertTrue(plays.skips(4, 11L, 2L))
        assertEquals(listOf(3 to 10L, 4 to 11L), backend.stops)
    }

    @Test
    fun `a play sent after a cancel isn't cancelled by it`() {
        val backend = Backend()
        val plays = backend.plays()
        plays.sent(1L, 3)
        assertTrue(plays.cancel())
        plays.sent(2L, 4)
        assertTrue(plays.skips(3, 10L, 1L))
        assertFalse(plays.skips(4, 11L, 2L))
        assertEquals(listOf(3 to 10L), backend.stops)
    }

    @Test
    fun `a reply that arrives while the play is still being sent is tracked and can be cancelled`() {
        val backend = Backend()
        val plays = backend.plays()
        val result = plays.send(1L, 7) {
            assertFalse(plays.skips(7, 1_000L, 1L))
            Result.success(Unit)
        }
        assertTrue(result.isSuccess)
        assertTrue(plays.cancel())
        assertEquals(listOf(7 to 1_000L), backend.stops)
        assertEquals(listOf(7 to 1_000L), backend.drops)
    }

    @Test
    fun `a play that fails to send is forgotten`() {
        val backend = Backend()
        val plays = backend.plays()
        val failed = plays.send(1L, 7) { Result.failure(IllegalStateException("offline")) }
        assertTrue(failed.isFailure)
        val thrown = plays.send(2L, 7) { throw IllegalStateException("closed") }
        assertTrue(thrown.isFailure)
        assertFalse(plays.cancel())
        assertFalse(plays.skips(7, 1_000L, 1L))
        assertFalse(plays.skips(7, 1_000L, 2L))
        assertTrue(backend.stops.isEmpty())
    }

    @Test
    fun `only a local development emote record is cleared without the server`() {
        assertTrue(endsDevPlayback(2_000_000_001, true, 2_000_000_001))
        assertTrue(endsDevPlayback(2_000_000_001, true, null))
        assertFalse(endsDevPlayback(2_000_000_001, true, 2_000_000_002))
        assertFalse(endsDevPlayback(2_000_000_001, false, 2_000_000_001))
        assertFalse(endsDevPlayback(7, false, null))
    }

    @Test
    fun `a snapshot leaves only the local development player alone`() {
        val local = UUID.fromString("11111111-2222-4333-8444-555555555555")
        assertTrue(snapshotSkips(local.toString(), local))
        assertTrue(snapshotSkips(local.toString().uppercase(), local))
        assertFalse(snapshotSkips("99999999-2222-4333-8444-555555555555", local))
        assertFalse(snapshotSkips(local.toString(), null))
        assertFalse(snapshotSkips("not-a-uuid", local))
    }

    @Test
    fun `a snapshot never restores a duo the local development player left`() {
        val local = UUID.fromString("11111111-2222-4333-8444-555555555555")
        val partner = "99999999-2222-4333-8444-555555555555"
        val other = "77777777-2222-4333-8444-555555555555"
        val third = "66666666-2222-4333-8444-555555555555"
        val anchor = EmoteAnchor(0.0, 64.0, 0.0, 0f)
        val old = SharedEmote(3, listOf(local.toString(), partner), anchor, 100L)
        val live = SharedEmote(4, listOf(other, third), anchor, 200L)
        val sessions = mapOf(local.toString() to old, partner to old, other to live, third to live)

        assertEquals(mapOf(other to live, third to live), snapshotSessions(sessions, local))
        assertEquals(sessions, snapshotSessions(sessions, null))
    }

    @Test
    fun `a snapshot drops the partner's play from a duo the local development player left`() {
        val local = UUID.fromString("11111111-2222-4333-8444-555555555555")
        val partner = "99999999-2222-4333-8444-555555555555"
        val other = "77777777-2222-4333-8444-555555555555"
        val third = "66666666-2222-4333-8444-555555555555"
        val anchor = EmoteAnchor(0.0, 64.0, 0.0, 0f)
        val old = SharedEmote(3, listOf(local.toString(), partner.uppercase()), anchor, 100L)
        val live = SharedEmote(4, listOf(other, third), anchor, 200L)
        val sessions = mapOf(local.toString() to old, partner to old, other to live, third to live)
        val active = mapOf(local.toString() to 3, partner to 3, other to 4, third to 4)
        val starts = mapOf(local.toString() to 100L, partner to 100L, other to 200L, third to 200L)

        assertEquals(mapOf(other to 4, third to 4), snapshotActive(active, starts, sessions, local))
        assertEquals(active, snapshotActive(active, starts, sessions, null))
    }

    @Test
    fun `a snapshot keeps a former partner's own solo play`() {
        val local = UUID.fromString("11111111-2222-4333-8444-555555555555")
        val partner = "99999999-2222-4333-8444-555555555555"
        val anchor = EmoteAnchor(0.0, 64.0, 0.0, 0f)
        val old = SharedEmote(3, listOf(local.toString(), partner), anchor, 100L)
        val sessions = mapOf(local.toString() to old, partner to old)

        assertEquals(mapOf(partner to 5), snapshotActive(mapOf(partner to 5), mapOf(partner to 300L), sessions, local))
        assertEquals(mapOf(partner to 3), snapshotActive(mapOf(partner to 3), mapOf(partner to 300L), sessions, local))
    }
}
