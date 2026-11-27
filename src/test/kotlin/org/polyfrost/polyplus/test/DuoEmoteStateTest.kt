package org.polyfrost.polyplus.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.EmoteRevision
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.polyfrost.polyplus.client.emotes.Emote
import org.polyfrost.polyplus.client.emotes.EmoteBundles
import org.polyfrost.polyplus.client.emotes.EmoteServerClock
import org.polyfrost.polyplus.client.emotes.duo.DuoEmoteState
import org.polyfrost.polyplus.client.emotes.duo.NoticeQueue
import org.polyfrost.polyplus.client.emotes.duo.duoNotice
import org.polyfrost.polyplus.client.emotes.duo.othersWaiting
import org.polyfrost.polyplus.client.emotes.playback.EmoteController
import org.polyfrost.polyplus.client.emotes.playback.EmoteController.HoldStep
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.network.http.responses.EmoteDefinition
import org.polyfrost.polyplus.client.network.http.responses.SharedEmote
import org.polyfrost.polyplus.client.network.http.responses.SharedEmotePrivacy
import org.polyfrost.polyplus.client.network.http.responses.SharedEmoteResult
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class DuoEmoteStateTest {
    private val saver = DuoEmoteState.privacySaver
    private val loader = DuoEmoteState.privacyLoader
    private val account = DuoEmoteState.accountProvider
    private val stopper = DuoEmoteState.stopSender
    private val requester = DuoEmoteState.requestSender
    private val responder = DuoEmoteState.responseSender
    private val canceller = DuoEmoteState.cancelSender

    @AfterEach
    fun restore() {
        DuoEmoteState.privacySaver = saver
        DuoEmoteState.privacyLoader = loader
        DuoEmoteState.accountProvider = account
        DuoEmoteState.stopSender = stopper
        DuoEmoteState.requestSender = requester
        DuoEmoteState.responseSender = responder
        DuoEmoteState.cancelSender = canceller
        DuoEmoteState.forgetUnanswered()
    }

    @Test
    fun `the latest privacy choice is the one saved last`() {
        val gate = CompletableDeferred<Unit>()
        val saved = ArrayList<SharedEmotePrivacy>()
        DuoEmoteState.privacySaver = { value ->
            saved += value
            if (value == SharedEmotePrivacy.Everyone) gate.await()
            Result.success(Unit)
        }
        runBlocking {
            val first = async(start = CoroutineStart.UNDISPATCHED) { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
            val middle = async(start = CoroutineStart.UNDISPATCHED) { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Friends) }
            val last = async(start = CoroutineStart.UNDISPATCHED) { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Nobody) }
            assertTrue(DuoEmoteState.savingPrivacy)
            gate.complete(Unit)
            assertNull(first.await())
            assertNull(middle.await())
            assertTrue(last.await()!!.isSuccess)
        }
        assertEquals(listOf(SharedEmotePrivacy.Everyone, SharedEmotePrivacy.Nobody), saved)
        assertEquals(SharedEmotePrivacy.Nobody, DuoEmoteState.privacy)
        assertFalse(DuoEmoteState.savingPrivacy)
    }

    @Test
    fun `a failed latest choice keeps what the server last accepted`() {
        val gate = CompletableDeferred<Unit>()
        DuoEmoteState.privacySaver = { value ->
            if (value == SharedEmotePrivacy.Friends) {
                gate.await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("offline"))
            }
        }
        runBlocking {
            val first = async(start = CoroutineStart.UNDISPATCHED) { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Friends) }
            val last = async(start = CoroutineStart.UNDISPATCHED) { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Nobody) }
            gate.complete(Unit)
            assertNull(first.await())
            assertTrue(last.await()!!.isFailure)
        }
        assertEquals(SharedEmotePrivacy.Friends, DuoEmoteState.privacy)
    }

    @Test
    fun `a rejected duo request clears the oldest pending ask`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        DuoEmoteState.noteAsk(first, 3)
        DuoEmoteState.noteAsk(second, 4)
        assertNull(DuoEmoteState.failAsk("player_offline", "", 12L))
        assertNull(DuoEmoteState.failAsk("shared_emote_not_found", "", null))
        val failed = DuoEmoteState.failAsk("player_offline", "That player is not online", null)!!
        assertEquals(first, failed.target)
        assertEquals(3, failed.emoteId)
        assertEquals(second, DuoEmoteState.failAsk("shared_emote_pending", "", null)?.target)
        assertNull(DuoEmoteState.failAsk("rate_limited", "", null))
        assertFalse(DuoEmoteState.hasSentAsks())
    }

    @Test
    fun `a rejected duo request is matched by its request id`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        DuoEmoteState.noteAsk(first, 3, 21L)
        DuoEmoteState.noteAsk(second, 4, 22L)
        assertNull(DuoEmoteState.failAsk("player_offline", "", 99L))
        val failed = DuoEmoteState.failAsk("player_offline", "That player is not online", 22L)!!
        assertEquals(second, failed.target)
        assertEquals(4, failed.emoteId)
        assertNull(DuoEmoteState.failAsk("player_offline", "", 22L))
        assertEquals(first, DuoEmoteState.failAsk("shared_emote_not_found", "", 21L)?.target)
        assertFalse(DuoEmoteState.hasSentAsks())
    }

    @Test
    fun `an error without a request id falls back to the oldest ask`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        DuoEmoteState.noteAsk(first, 3, 31L)
        DuoEmoteState.noteAsk(second, 4, 32L)
        assertNull(DuoEmoteState.failAsk("shared_emote_not_found", "", null))
        assertEquals(first, DuoEmoteState.failAsk("rate_limited", "", null)?.target)
        assertEquals(second, DuoEmoteState.failAsk("player_offline", "", 32L)?.target)
        assertFalse(DuoEmoteState.hasSentAsks())
    }

    @Test
    fun `a cancelled ask that fails doesn't swallow the retry`() {
        val target = UUID(5L, 5L)
        val emoteId = 876_601
        DuoEmoteState.stopSender = { Result.success(Unit) }
        try {
            DuoEmoteState.noteAsk(target, emoteId, 41L)
            assertTrue(DuoEmoteState.cancelSentAsks())
            assertFalse(DuoEmoteState.hasSentAsks())
            assertNull(DuoEmoteState.failAsk("player_offline", "", 41L))
            DuoEmoteState.noteAsk(target, emoteId, 42L)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-retry", target.toString(), emoteId, Long.MAX_VALUE))
            assertEquals("dr-retry", DuoEmoteState.outgoingRequests().single().requestId)
            assertFalse(DuoEmoteState.hasSentAsks())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a cancelled ask's own ack is dropped and the next one kept`() {
        val target = UUID(6L, 6L)
        val emoteId = 876_602
        DuoEmoteState.stopSender = { Result.success(Unit) }
        try {
            DuoEmoteState.noteAsk(target, emoteId, 51L)
            assertTrue(DuoEmoteState.cancelSentAsks())
            DuoEmoteState.noteAsk(target, emoteId, 52L)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-old", target.toString(), emoteId, Long.MAX_VALUE))
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-new", target.toString(), emoteId, Long.MAX_VALUE))
            assertEquals("dr-new", DuoEmoteState.outgoingRequests().single().requestId)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an ask whose stop didn't send stays pending`() {
        val target = UUID(7L, 7L)
        DuoEmoteState.stopSender = { Result.failure(IllegalStateException("offline")) }
        DuoEmoteState.noteAsk(target, 3, 61L)
        assertTrue(DuoEmoteState.cancelSentAsks())
        assertTrue(DuoEmoteState.hasSentAsks())
        assertEquals(target, DuoEmoteState.failAsk("player_offline", "", 61L)?.target)
    }

    @Test
    fun `stopping shared play cancels an accepted request before it can start`() {
        val partner = UUID(12L, 12L)
        val emoteId = 876_614
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-clear", partner.toString(), emoteId, Long.MAX_VALUE))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-clear", SharedEmoteResult.Accepted))
            assertTrue(DuoEmoteState.stopShared(null)?.isSuccess == true)
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
            assertTrue(DuoEmoteState.cancelledStart(emoteId, partner))
            assertNull(DuoEmoteState.stopShared(null))
            assertEquals(1, stops)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-clear-again", partner.toString(), emoteId, Long.MAX_VALUE))
            assertFalse(DuoEmoteState.cancelledStart(emoteId, partner))
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `stopping shared play leaves an unanswered incoming prompt alone`() {
        val emoteId = 876_616
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/prompt.zip", hash = "a".repeat(64))
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequested("dr-prompt", UUID(14L, 14L).toString(), emoteId, Long.MAX_VALUE, sent),
            )
            assertNull(DuoEmoteState.stopShared(null))
            assertEquals(0, stops)
            assertEquals("dr-prompt", DuoEmoteState.incomingRequests().single().requestId)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a failed shared stop keeps the requests open`() {
        val partner = UUID(13L, 13L)
        DuoEmoteState.stopSender = { Result.failure(IllegalStateException("offline")) }
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-offline", partner.toString(), 876_615, Long.MAX_VALUE))
            assertTrue(DuoEmoteState.stopShared(null)?.isFailure == true)
            assertEquals("dr-offline", DuoEmoteState.outgoingRequests().single().requestId)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a local play cancels an ask that hasn't been acknowledged yet`() {
        val target = UUID(15L, 15L)
        val emoteId = 876_617
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        try {
            DuoEmoteState.noteAsk(target, emoteId, 71L)
            assertNull(DuoEmoteState.stopForLocalPlay(null))
            assertEquals(1, stops)
            assertFalse(DuoEmoteState.hasSentAsks())
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-local-ask", target.toString(), emoteId, Long.MAX_VALUE))
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a local play cancels a request still waiting for an answer`() {
        val partner = UUID(16L, 16L)
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-local-wait", partner.toString(), 876_618, Long.MAX_VALUE))
            assertNull(DuoEmoteState.stopForLocalPlay(null))
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a local play cancels an accepted request before both reach their marks`() {
        val emoteId = 876_619
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/accepted.zip", hash = "b".repeat(64))
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequested("dr-local-accepted", UUID(17L, 17L).toString(), emoteId, Long.MAX_VALUE, sent),
            )
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-local-accepted", SharedEmoteResult.Accepted))
            assertNull(DuoEmoteState.stopForLocalPlay(null))
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.incomingRequests().isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `stopping cancels an acceptance the server hasn't confirmed yet`() {
        val emoteId = 876_622
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/accepting.zip", hash = "d".repeat(64))
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.responseSender = { Result.success(Unit) }
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequested("dr-accepting", UUID(20L, 20L).toString(), emoteId, Long.MAX_VALUE, sent),
            )
            assertFalse(DuoEmoteState.hasOpenRequests())
            assertTrue(DuoEmoteState.respond("dr-accepting", accept = true).isSuccess)
            assertTrue(DuoEmoteState.hasOpenRequests())
            assertTrue(DuoEmoteState.stopShared(null)?.isSuccess == true)
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.incomingRequests().isEmpty())
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequested("dr-accepting-2", UUID(20L, 20L).toString(), emoteId, Long.MAX_VALUE, sent),
            )
            assertTrue(DuoEmoteState.respond("dr-accepting-2", accept = true).isSuccess)
            assertNull(DuoEmoteState.stopForLocalPlay(null))
            assertEquals(2, stops)
            assertTrue(DuoEmoteState.incomingRequests().isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an acceptance sent near the deadline stays open until the server answers`() {
        val emoteId = 876_624
        val partner = UUID(22L, 22L)
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/late.zip", hash = "f".repeat(64))
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.responseSender = { Result.success(Unit) }
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            val expired = EmoteServerClock.now() - 1_000L
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequested("dr-late", partner.toString(), emoteId, expired, sent))
            assertFalse(DuoEmoteState.hasOpenRequests())
            assertTrue(DuoEmoteState.respond("dr-late", accept = true).isSuccess)
            assertTrue(DuoEmoteState.hasOpenRequests())
            assertTrue(DuoEmoteState.stopShared(null)?.isSuccess == true)
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.cancelledStart(emoteId, partner))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-late", SharedEmoteResult.Accepted))
            assertFalse(DuoEmoteState.hasOpenRequests())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a sent acceptance past the response grace no longer counts as open`() {
        val emoteId = 876_625
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/stale.zip", hash = "0".repeat(64))
        DuoEmoteState.responseSender = { Result.success(Unit) }
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            val stale = EmoteServerClock.now() - 30_000L
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequested("dr-stale", UUID(23L, 23L).toString(), emoteId, stale, sent))
            assertTrue(DuoEmoteState.respond("dr-stale", accept = true).isSuccess)
            assertFalse(DuoEmoteState.hasOpenRequests())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an outgoing request past its deadline stays cancellable until the answer arrives`() {
        val emoteId = 876_626
        val partner = UUID(24L, 24L)
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        try {
            val expired = EmoteServerClock.now() - 1_000L
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-late-out", partner.toString(), emoteId, expired))
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
            assertTrue(DuoEmoteState.hasOpenRequests())
            assertTrue(DuoEmoteState.stopShared(null)?.isSuccess == true)
            assertEquals(1, stops)
            assertFalse(DuoEmoteState.hasOpenRequests())
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-late-out", SharedEmoteResult.Accepted))
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
            assertFalse(DuoEmoteState.hasOpenRequests())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a late acceptance of an outgoing request still starts the approach`() {
        val emoteId = 876_627
        val partner = UUID(25L, 25L)
        try {
            val expired = EmoteServerClock.now() - 1_000L
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-late-ok", partner.toString(), emoteId, expired))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-late-ok", SharedEmoteResult.Accepted))
            assertTrue(DuoEmoteState.outgoingRequests().single().accepted)
            assertTrue(DuoEmoteState.hasOpenRequests())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an outgoing request past its deadline is kept until the backend says it expired`() {
        val partner = UUID(27L, 27L)
        try {
            val expired = EmoteServerClock.now() - 1_000L
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-unanswered", partner.toString(), 876_629, expired))
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
            assertEquals(listOf("dr-unanswered"), DuoEmoteState.unresolvedRequests().map { it.requestId })
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-unanswered", SharedEmoteResult.Expired))
            assertTrue(DuoEmoteState.unresolvedRequests().isEmpty())
            assertEquals(SharedEmoteResult.Expired, DuoEmoteState.resultOf("dr-unanswered"))
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a refusal that lands with its request before a tick still reports the answer`() {
        val partner = UUID(31L, 31L)
        try {
            val until = EmoteServerClock.now() + 10_000L
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-refused", partner.toString(), 876_631, until))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-refused", SharedEmoteResult.Declined))
            val current = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
            assertTrue(current.isEmpty())
            val ended = DuoEmoteState.followUp(emptyMap(), current)
            assertEquals(listOf("dr-refused"), ended.map { it.request.requestId })
            assertEquals(SharedEmoteResult.Declined, ended.single().result)
            assertEquals(partner, ended.single().request.partner)
            assertFalse(ended.single().request.incoming)
            assertTrue(DuoEmoteState.followUp(emptyMap(), current).isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a request already followed is reported once when it resolves`() {
        val partner = UUID(32L, 32L)
        try {
            val until = EmoteServerClock.now() + 10_000L
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-followed", partner.toString(), 876_632, until))
            val known = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
            assertTrue(DuoEmoteState.followUp(emptyMap(), known).isEmpty())
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-followed", SharedEmoteResult.Declined))
            val current = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
            val ended = DuoEmoteState.followUp(known, current)
            assertEquals(listOf(SharedEmoteResult.Declined), ended.map { it.result })
            assertTrue(DuoEmoteState.followUp(known, current).isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an incoming request cancelled before it was shown leaves no notice`() {
        val partner = UUID(33L, 33L)
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            val until = EmoteServerClock.now() + 10_000L
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequested("dr-unseen", partner.toString(), 876_633, until))
            assertEquals("dr-unseen", DuoEmoteState.incomingRequests().single().requestId)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-unseen", SharedEmoteResult.Cancelled))
            assertTrue(DuoEmoteState.followUp(emptyMap(), emptyMap()).isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an outgoing request past the response grace no longer counts as open`() {
        try {
            val stale = EmoteServerClock.now() - 30_000L
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-stale-out", UUID(26L, 26L).toString(), 876_628, stale))
            assertFalse(DuoEmoteState.hasOpenRequests())
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
            assertTrue(DuoEmoteState.unresolvedRequests().isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an acceptance that didn't send leaves the prompt unanswered`() {
        val emoteId = 876_623
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/unsent.zip", hash = "e".repeat(64))
        DuoEmoteState.responseSender = { Result.failure(IllegalStateException("offline")) }
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequested("dr-unsent", UUID(21L, 21L).toString(), emoteId, Long.MAX_VALUE, sent),
            )
            assertTrue(DuoEmoteState.respond("dr-unsent", accept = true).isFailure)
            assertFalse(DuoEmoteState.hasOpenRequests())
            assertNull(DuoEmoteState.stopShared(null))
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a local play leaves an unanswered incoming prompt alone`() {
        val emoteId = 876_620
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/local-prompt.zip", hash = "c".repeat(64))
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequested("dr-local-prompt", UUID(18L, 18L).toString(), emoteId, Long.MAX_VALUE, sent),
            )
            assertNull(DuoEmoteState.stopForLocalPlay(null))
            assertEquals(0, stops)
            assertEquals("dr-local-prompt", DuoEmoteState.incomingRequests().single().requestId)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a local play whose stop didn't send keeps the request open`() {
        val partner = UUID(19L, 19L)
        DuoEmoteState.stopSender = { Result.failure(IllegalStateException("offline")) }
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-local-offline", partner.toString(), 876_621, Long.MAX_VALUE))
            assertNull(DuoEmoteState.stopForLocalPlay(null))
            assertEquals("dr-local-offline", DuoEmoteState.outgoingRequests().single().requestId)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a session from a snapshot keeps the revision it started with`() {
        val emoteId = 876_603
        val a = UUID(8L, 8L).toString()
        val b = UUID(9L, 9L).toString()
        val session = SharedEmote(emoteId, listOf(a, b), EmoteAnchor(0.0, 64.0, 0.0, 0f), 1_000L)
        val started = EmoteDefinition(emoteId, url = "https://example.invalid/started.zip", hash = "e".repeat(64))
        try {
            DuoEmoteState.applySnapshot(listOf(a, b), emptyMap(), emptyMap(), emptyMap(), mapOf(a to session, b to session)) { players, id ->
                started.takeIf { id == emoteId && a in players }
            }
            assertEquals(EmoteRevision(emoteId, started.hash), DuoEmoteState.revisionOf(session))
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a privacy choice keeps its place even when its save starts late`() {
        val saved = ArrayList<SharedEmotePrivacy>()
        DuoEmoteState.privacySaver = { value ->
            saved += value
            Result.success(Unit)
        }
        val everyone = DuoEmoteState.choosePrivacy(SharedEmotePrivacy.Everyone)
        val nobody = DuoEmoteState.choosePrivacy(SharedEmotePrivacy.Nobody)
        runBlocking {
            assertTrue(DuoEmoteState.savePrivacy(nobody)!!.isSuccess)
            assertNull(DuoEmoteState.savePrivacy(everyone))
        }
        assertEquals(listOf(SharedEmotePrivacy.Nobody), saved)
        assertEquals(SharedEmotePrivacy.Nobody, DuoEmoteState.privacy)
        assertFalse(DuoEmoteState.savingPrivacy)
    }

    @Test
    fun `a save that finishes after an account switch changes nothing`() {
        val gate = CompletableDeferred<Unit>()
        DuoEmoteState.privacySaver = {
            gate.await()
            Result.success(Unit)
        }
        DuoEmoteState.accountProvider = { UUID(1L, 1L) }
        runBlocking {
            val save = async(start = CoroutineStart.UNDISPATCHED) { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Friends) }
            DuoEmoteState.accountProvider = { UUID(2L, 2L) }
            DuoEmoteState.reset()
            gate.complete(Unit)
            assertNull(save.await())
        }
        assertEquals(SharedEmotePrivacy.Unknown, DuoEmoteState.privacy)
        assertFalse(DuoEmoteState.savingPrivacy)
    }

    @Test
    fun `leaving a server on the same account keeps a save in flight`() {
        val gate = CompletableDeferred<Unit>()
        DuoEmoteState.privacySaver = {
            gate.await()
            Result.success(Unit)
        }
        DuoEmoteState.accountProvider = { UUID(3L, 3L) }
        runBlocking {
            val save = async(start = CoroutineStart.UNDISPATCHED) { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Friends) }
            DuoEmoteState.reset()
            gate.complete(Unit)
            assertTrue(save.await()!!.isSuccess)
        }
        assertEquals(SharedEmotePrivacy.Friends, DuoEmoteState.privacy)
        assertFalse(DuoEmoteState.savingPrivacy)
    }

    @Test
    fun `a refresh read before a save never overwrites it`() {
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        val read = CompletableDeferred<Unit>()
        DuoEmoteState.privacyLoader = {
            read.await()
            Result.success(SharedEmotePrivacy.Everyone)
        }
        runBlocking {
            val refresh = async(start = CoroutineStart.UNDISPATCHED) { DuoEmoteState.loadPrivacy(DuoEmoteState.beginRefresh()) }
            assertTrue(DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Nobody)!!.isSuccess)
            read.complete(Unit)
            refresh.await()
        }
        assertEquals(SharedEmotePrivacy.Nobody, DuoEmoteState.privacy)
    }

    @Test
    fun `an incoming duo request keeps the revision it was sent with`() {
        val emoteId = 876_543
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/sent.zip", hash = "c".repeat(64))
        val newer = EmoteDefinition(emoteId, url = "https://example.invalid/newer.zip", hash = "d".repeat(64))
        val holder = Any()
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequested("dr-1", UUID(4L, 4L).toString(), emoteId, Long.MAX_VALUE, sent),
            )
            CosmeticCatalog.pinEmote(holder, emoteId, newer)
            val request = DuoEmoteState.incomingRequests().single { it.requestId == "dr-1" }
            assertEquals(EmoteRevision(emoteId, sent.hash), request.revision)
        } finally {
            CosmeticCatalog.releaseEmotePin(holder)
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a request acknowledged before the send returns leaves no stale ask`() {
        val target = UUID(10L, 10L)
        val emoteId = 876_612
        try {
            DuoEmoteState.requestSender = {
                DuoEmoteState.handle(
                    ClientboundPacket.SharedEmoteRequestSent("dr-fast", target.toString(), emoteId, Long.MAX_VALUE),
                )
                Result.success(Unit)
            }
            assertTrue(DuoEmoteState.request(emoteId, target).isSuccess)
            assertFalse(DuoEmoteState.hasSentAsks())
            assertEquals("dr-fast", DuoEmoteState.outgoingRequests().single().requestId)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a request that fails to send forgets its ask`() {
        val target = UUID(11L, 11L)
        DuoEmoteState.requestSender = { Result.failure(IllegalStateException("offline")) }
        assertTrue(DuoEmoteState.request(876_613, target).isFailure)
        assertFalse(DuoEmoteState.hasSentAsks())
        DuoEmoteState.requestSender = { throw IllegalStateException("closed") }
        assertTrue(DuoEmoteState.request(876_613, target).isFailure)
        assertFalse(DuoEmoteState.hasSentAsks())
    }

    @Test
    fun `an outgoing duo request takes the revision the backend pinned`() {
        val target = UUID(8L, 8L)
        val emoteId = 876_610
        val local = EmoteRevision(emoteId, "e".repeat(64))
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/sent.zip", hash = "f".repeat(64))
        try {
            DuoEmoteState.noteAsk(target, emoteId, 71L, local)
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequestSent("dr-pinned", target.toString(), emoteId, Long.MAX_VALUE, sent),
            )
            val pinned = EmoteRevision(emoteId, sent.hash)
            assertEquals(pinned, DuoEmoteState.outgoingRequests().single().revision)
            assertTrue(pinned in CosmeticCatalog.pinnedEmoteRevisions())
            DuoEmoteState.reset()
            assertFalse(pinned in CosmeticCatalog.pinnedEmoteRevisions())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an outgoing duo request from an old backend keeps the local revision`() {
        val target = UUID(9L, 9L)
        val emoteId = 876_611
        val local = EmoteRevision(emoteId, "a".repeat(64))
        try {
            DuoEmoteState.noteAsk(target, emoteId, 81L, local)
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequestSent("dr-legacy", target.toString(), emoteId, Long.MAX_VALUE),
            )
            assertEquals(local, DuoEmoteState.outgoingRequests().single().revision)
            assertFalse(local in CosmeticCatalog.pinnedEmoteRevisions())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a partner role counts its start delay before finishing`() {
        assertFalse(Emote.roleDone(4f, false, 4_000L, 5f))
        assertFalse(Emote.roleDone(4f, false, 8_999L, 5f))
        assertTrue(Emote.roleDone(4f, false, 9_000L, 5f))
        assertFalse(Emote.roleDone(4f, false, 60_000L, Float.NaN))
    }

    @Test
    fun `a finished role in a shared session holds until its partner finishes`() {
        val ended = Renderer.EMOTE_POSING or Renderer.EMOTE_ENDED
        val held = Renderer.EMOTE_POSING or Renderer.EMOTE_HELD
        val yes = { true }
        val no = { false }
        assertEquals(HoldStep.PLAY, EmoteController.holdStep(Renderer.EMOTE_POSING, false, yes, yes))
        assertEquals(HoldStep.HOLD, EmoteController.holdStep(ended, false, yes, no))
        assertEquals(HoldStep.HOLD, EmoteController.holdStep(held, true, { error("asked twice") }, no))
        assertEquals(HoldStep.END, EmoteController.holdStep(held, true, yes, yes))
        assertEquals(HoldStep.END, EmoteController.holdStep(ended, false, yes, yes))
    }

    @Test
    fun `a finished role outside a shared session stops instead of holding`() {
        val ended = Renderer.EMOTE_POSING or Renderer.EMOTE_ENDED
        assertEquals(HoldStep.END, EmoteController.holdStep(ended, false, { false }, { false }))
    }

    @Test
    fun `a held role waits for its partner however long it plays and ends on a cancel`() {
        val held = Renderer.EMOTE_POSING or Renderer.EMOTE_HELD
        val cancel = Renderer.EMOTE_POSING or Renderer.EMOTE_CANCEL or Renderer.EMOTE_HELD
        assertEquals(HoldStep.HOLD, EmoteController.holdStep(held, true, { true }, { false }))
        assertEquals(HoldStep.END, EmoteController.holdStep(cancel, true, { true }, { false }))
        assertEquals(HoldStep.END, EmoteController.holdStep(-1, true, { true }, { false }))
    }

    @Test
    fun `a duo partner can shuffle on the mark without ending the shared emote`() {
        val walked = Renderer.EMOTE_POSING or Renderer.EMOTE_CANCEL or Renderer.EMOTE_CANCEL_WALKING
        val sprinted = walked or Renderer.EMOTE_CANCEL_SPRINTING
        val swung = walked or Renderer.EMOTE_CANCEL_SWINGING
        val crouched = Renderer.EMOTE_POSING or Renderer.EMOTE_CANCEL or Renderer.EMOTE_CANCEL_CROUCHING
        val yes = { true }
        val no = { false }
        assertEquals(HoldStep.PLAY, EmoteController.holdStep(walked, false, yes, no, yes))
        assertEquals(HoldStep.PLAY, EmoteController.holdStep(sprinted, false, yes, no, yes))
        assertEquals(HoldStep.END, EmoteController.holdStep(walked, false, yes, no, no))
        assertEquals(HoldStep.END, EmoteController.holdStep(walked, false, yes, no))
        assertEquals(HoldStep.END, EmoteController.holdStep(swung, false, yes, no, yes))
        assertEquals(HoldStep.END, EmoteController.holdStep(crouched, false, yes, no, yes))
        assertEquals(HoldStep.HOLD, EmoteController.holdStep(walked or Renderer.EMOTE_HELD, true, yes, no, yes))
    }

    @Test
    fun `emote start delays are read from the bundle animation files`(@TempDir dir: Path) {
        Files.writeString(
            dir.resolve("duo.animation.json"),
            """{"animations":{"animation.duo.a":{"start_delay":1.25},"animation.duo.b":{"start_delay":"0.5"},""" +
                """"animation.duo.c":{"start_delay":"math.random(0, 1)"},"animation.duo.d":{}}}""",
        )
        val delays = EmoteBundles.startDelays(dir)
        assertEquals(1.25f, delays["animation.duo.a"])
        assertEquals(0.5f, delays["animation.duo.b"])
        assertTrue(delays["animation.duo.c"]!!.isNaN())
        assertFalse("animation.duo.d" in delays)
    }

    @Test
    fun `a looping partner role never counts as finished`() {
        assertFalse(Emote.roleDone(1f, true, 60_000L))
        assertTrue(Emote.roleDone(1f, false, 1_000L))
        assertFalse(Emote.roleDone(1f, false, 999L))
        assertFalse(Emote.roleDone(0f, false, 60_000L))
    }

    @Test
    fun `leaving a server cancels open duo requests with the backend`() {
        val emoteId = 876_640
        val sent = EmoteDefinition(emoteId, url = "https://example.invalid/leave.zip", hash = "f".repeat(64))
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
        try {
            DuoEmoteState.handle(
                ClientboundPacket.SharedEmoteRequested("dr-leave-in", UUID(40L, 40L).toString(), emoteId, Long.MAX_VALUE, sent),
            )
            DuoEmoteState.leaveServer()
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.unresolvedRequests().isEmpty())
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-leave-out", UUID(41L, 41L).toString(), emoteId, Long.MAX_VALUE))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-leave-out", SharedEmoteResult.Accepted))
            DuoEmoteState.leaveServer()
            assertEquals(2, stops)
            DuoEmoteState.noteAsk(UUID(42L, 42L), emoteId, 71L)
            DuoEmoteState.leaveServer()
            assertEquals(3, stops)
            assertFalse(DuoEmoteState.hasSentAsks())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `leaving a server in a duo emote stops it with the backend`() {
        val local = UUID(43L, 43L)
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.accountProvider = { local }
        try {
            val session = SharedEmote(876_641, listOf(local.toString(), UUID(44L, 44L).toString()), EmoteAnchor(0.0, 0.0, 0.0, 0f), 0L)
            DuoEmoteState.applySnapshot(emptyList(), emptyMap(), emptyMap(), emptyMap(), mapOf(local.toString() to session))
            DuoEmoteState.leaveServer()
            assertEquals(1, stops)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `leaving a server with nothing open sends no stop`() {
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        try {
            DuoEmoteState.leaveServer()
            assertEquals(0, stops)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `notices told in the same tick are shown one after another`() {
        val notices = NoticeQueue(3_500L)
        notices.add("Alex said no")
        notices.add("Sam said no")
        assertEquals("Alex said no", notices.show(1_000L))
        var now = 1_000L
        while (now < 4_450L) {
            now += 50L
            assertEquals("Alex said no", notices.show(now))
        }
        assertEquals("Sam said no", notices.show(now + 50L))
    }

    @Test
    fun `a notice waits while another card has the screen and then gets its full time`() {
        val notices = NoticeQueue(3_500L)
        notices.add("Alex said no")
        assertEquals("Alex said no", notices.show(60_000L))
        var now = 60_000L
        while (now < 63_450L) {
            now += 50L
            assertEquals("Alex said no", notices.show(now))
        }
        assertNull(notices.show(now + 100L))
        notices.add("Sam said no")
        assertEquals("Sam said no", notices.show(100_000L))
        assertEquals("Sam said no", notices.show(150_000L))
        assertEquals("Sam said no", notices.show(150_050L))
    }

    @Test
    fun `a repeated notice isn't queued twice`() {
        val notices = NoticeQueue(3_500L)
        notices.add("Nobody nearby")
        notices.add("Nobody nearby")
        assertEquals("Nobody nearby", notices.show(0L))
        var now = 0L
        while (now < 2_000L) {
            now += 50L
            notices.show(now)
        }
        notices.add("Nobody nearby")
        while (now < 5_450L) {
            now += 50L
            assertEquals("Nobody nearby", notices.show(now))
        }
        assertNull(notices.show(now + 50L))
        notices.add("Sam said no")
        notices.clear()
        assertNull(notices.show(now + 100L))
    }

    @Test
    fun `a cancelled prompt only notifies when the player saw it`() {
        val partner = UUID(45L, 45L)
        val prompt = DuoEmoteState.Request("dr-hidden", partner, 876_642, Long.MAX_VALUE, incoming = true)
        assertNull(duoNotice(prompt, SharedEmoteResult.Cancelled, "Alex", shown = false))
        assertEquals("The duo emote was cancelled", duoNotice(prompt, SharedEmoteResult.Cancelled, "Alex", shown = true))
        val accepted = prompt.copy(accepted = true)
        assertEquals("The duo emote was cancelled", duoNotice(accepted, SharedEmoteResult.Cancelled, "Alex", shown = false))
        val outgoing = prompt.copy(incoming = false)
        assertEquals("The duo emote was cancelled", duoNotice(outgoing, SharedEmoteResult.Cancelled, "Alex", shown = false))
        assertEquals("Alex said no", duoNotice(outgoing, SharedEmoteResult.Declined, "Alex", shown = false))
    }

    private fun allowRequests() {
        DuoEmoteState.privacySaver = { Result.success(Unit) }
        runBlocking { DuoEmoteState.updatePrivacy(SharedEmotePrivacy.Everyone) }
        DuoEmoteState.beginRefresh()
    }

    @Test
    fun `a partner leaving the tab list cancels an accepted request with the backend`() {
        val local = UUID(50L, 50L)
        val partner = UUID(51L, 51L)
        val emoteId = 876_650
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.accountProvider = { local }
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-gone", partner.toString(), emoteId, Long.MAX_VALUE))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-gone", SharedEmoteResult.Accepted))
            val known = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
            DuoEmoteState.forget(partner)
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.unresolvedRequests().isEmpty())
            assertTrue(DuoEmoteState.cancelledStart(emoteId, partner))
            val current = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
            val ended = DuoEmoteState.followUp(known, current)
            val notices = ended.mapNotNull { duoNotice(it.request, it.result, "Alex", shown = false) }
            assertEquals(listOf("The duo emote was cancelled"), notices)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-gone", SharedEmoteResult.Cancelled))
            assertTrue(DuoEmoteState.followUp(current, current).isEmpty())
            assertNull(DuoEmoteState.stopShared(null))
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a partner leaving the tab list cancels their unanswered request without saying no`() {
        val partner = UUID(52L, 52L)
        val responses = ArrayList<ServerboundPacket.SharedEmoteResponse>()
        val cancels = ArrayList<ServerboundPacket.SharedEmoteCancel>()
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.responseSender = { responses += it; Result.success(Unit) }
        DuoEmoteState.cancelSender = { cancels += it; Result.success(Unit) }
        allowRequests()
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequested("dr-gone-prompt", partner.toString(), 876_651, Long.MAX_VALUE))
            val known = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
            DuoEmoteState.forget(partner)
            assertEquals(0, stops)
            assertTrue(responses.isEmpty())
            assertEquals(listOf("dr-gone-prompt"), cancels.map { it.requestId })
            assertTrue(DuoEmoteState.incomingRequests().isEmpty())
            val ended = DuoEmoteState.followUp(known, emptyMap()).filter { it.result != null }
            assertEquals(listOf(SharedEmoteResult.Cancelled), ended.map { it.result })
            assertEquals("The duo emote was cancelled", duoNotice(ended.single().request, ended.single().result, "Alex", shown = true))
            assertNull(duoNotice(ended.single().request, ended.single().result, "Alex", shown = false))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-gone-prompt", SharedEmoteResult.Cancelled))
            assertTrue(DuoEmoteState.followUp(emptyMap(), emptyMap()).isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a partner who cancels an outgoing request is reported as a cancel, not a no`() {
        val partner = UUID(53L, 53L)
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-partner-left", partner.toString(), 876_652, Long.MAX_VALUE))
            val known = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-partner-left", SharedEmoteResult.Cancelled))
            val ended = DuoEmoteState.followUp(known, emptyMap())
            val notices = ended.mapNotNull { duoNotice(it.request, it.result, "Alex", shown = false) }
            assertEquals(listOf("The duo emote was cancelled"), notices)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `saying no to a request still sends a decline`() {
        val partner = UUID(54L, 54L)
        val responses = ArrayList<ServerboundPacket.SharedEmoteResponse>()
        val cancels = ArrayList<ServerboundPacket.SharedEmoteCancel>()
        DuoEmoteState.responseSender = { responses += it; Result.success(Unit) }
        DuoEmoteState.cancelSender = { cancels += it; Result.success(Unit) }
        allowRequests()
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequested("dr-said-no", partner.toString(), 876_653, Long.MAX_VALUE))
            assertTrue(DuoEmoteState.respond("dr-said-no", accept = false).isSuccess)
            assertEquals(listOf("dr-said-no"), responses.map { it.requestId })
            assertFalse(responses.single().accept)
            assertTrue(cancels.isEmpty())
            assertTrue(DuoEmoteState.incomingRequests().isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a duo cancel is sent with its request id`() {
        val packet: ServerboundPacket = ServerboundPacket.SharedEmoteCancel("dr-wire")
        val json = Json.parseToJsonElement(Json.encodeToString(ServerboundPacket.serializer(), packet)).jsonObject
        assertEquals(setOf("type", "request_id"), json.keys)
        assertEquals("SharedEmoteCancel", json["type"]?.jsonPrimitive?.content)
        assertEquals("dr-wire", json["request_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `an accepted request gives the pair ten seconds to start`() {
        val partner = UUID(55L, 55L)
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-window", partner.toString(), 876_654, Long.MAX_VALUE))
            val before = EmoteServerClock.now()
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-window", SharedEmoteResult.Accepted))
            val after = EmoteServerClock.now()
            val request = DuoEmoteState.outgoingRequests().single()
            assertTrue(request.accepted)
            assertTrue(request.expiresAt in (before + 10_000L)..(after + 10_000L))
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a partner leaving hides their request while a stop would cancel someone else's`() {
        val local = UUID(53L, 53L)
        val partner = UUID(54L, 54L)
        val other = UUID(55L, 55L)
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.accountProvider = { local }
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-gone-kept", partner.toString(), 876_652, Long.MAX_VALUE))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-still-here", other.toString(), 876_653, Long.MAX_VALUE))
            DuoEmoteState.forget(partner)
            assertEquals(0, stops)
            assertEquals(listOf("dr-still-here"), DuoEmoteState.outgoingRequests().map { it.requestId })
            assertTrue(DuoEmoteState.stopShared(null)?.isSuccess == true)
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
            assertFalse(DuoEmoteState.hasOpenRequests())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a partner leaving whose stop didn't send keeps the request cancellable`() {
        val partner = UUID(56L, 56L)
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.failure(IllegalStateException("offline")) }
        DuoEmoteState.accountProvider = { UUID(57L, 57L) }
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-gone-offline", partner.toString(), 876_654, Long.MAX_VALUE))
            DuoEmoteState.forget(partner)
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
            assertTrue(DuoEmoteState.hasOpenRequests())
            val now = System.currentTimeMillis()
            DuoEmoteState.cancelDeparted(now)
            assertEquals(1, stops)
            DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
            DuoEmoteState.cancelDeparted(now + 2_000L)
            assertEquals(2, stops)
            assertFalse(DuoEmoteState.hasOpenRequests())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a partner leaving mid duo emote stops it and keeps the session until the stop comes back`() {
        val local = UUID(60L, 60L)
        val partner = UUID(61L, 61L)
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.accountProvider = { local }
        try {
            val players = listOf(local.toString(), partner.toString())
            val session = SharedEmote(876_660, players, EmoteAnchor(1.0, 2.0, 3.0, 90f), 0L)
            DuoEmoteState.applySnapshot(emptyList(), emptyMap(), emptyMap(), emptyMap(), mapOf(local.toString() to session))
            DuoEmoteState.forget(partner)
            assertEquals(1, stops)
            assertEquals(session, DuoEmoteState.sessionOf(local))
            assertNull(DuoEmoteState.sessionOf(partner))
            DuoEmoteState.cancelDeparted(System.currentTimeMillis() + 2_000L)
            assertEquals(1, stops)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteStop(players))
            assertNull(DuoEmoteState.sessionOf(local))
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a duo emote whose partner left keeps retrying the stop even with other requests open`() {
        val local = UUID(62L, 62L)
        val partner = UUID(63L, 63L)
        val other = UUID(64L, 64L)
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.failure(IllegalStateException("offline")) }
        DuoEmoteState.accountProvider = { local }
        allowRequests()
        try {
            val session = SharedEmote(876_661, listOf(local.toString(), partner.toString()), EmoteAnchor(0.0, 0.0, 0.0, 0f), 0L)
            DuoEmoteState.applySnapshot(emptyList(), emptyMap(), emptyMap(), emptyMap(), mapOf(local.toString() to session))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequested("dr-meanwhile", other.toString(), 876_662, Long.MAX_VALUE))
            DuoEmoteState.forget(partner)
            assertEquals(1, stops)
            assertEquals(session, DuoEmoteState.sessionOf(local))
            DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
            DuoEmoteState.cancelDeparted(System.currentTimeMillis() + 2_000L)
            assertEquals(2, stops)
            assertTrue(DuoEmoteState.incomingRequests().isEmpty())
            assertEquals(session, DuoEmoteState.sessionOf(local))
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an accepted request with a partner who left gives way to the next accepted one`() {
        val local = UUID(65L, 65L)
        val partner = UUID(66L, 66L)
        val other = UUID(67L, 67L)
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.responseSender = { Result.success(Unit) }
        DuoEmoteState.accountProvider = { local }
        allowRequests()
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-left", partner.toString(), 876_663, Long.MAX_VALUE))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-left", SharedEmoteResult.Accepted))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequested("dr-stayed", other.toString(), 876_664, Long.MAX_VALUE))
            assertTrue(DuoEmoteState.respond("dr-stayed", accept = true).isSuccess)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-stayed", SharedEmoteResult.Accepted))
            val known = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
            DuoEmoteState.forget(partner)
            assertEquals(0, stops)
            val approach = DuoEmoteState.outgoingRequests().firstOrNull { it.accepted }
                ?: DuoEmoteState.incomingRequests().firstOrNull { it.accepted }
            assertEquals("dr-stayed", approach?.requestId)
            val current = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
            assertEquals(setOf("dr-stayed"), current.keys)
            val notices = DuoEmoteState.followUp(known, current).mapNotNull { duoNotice(it.request, it.result, "Alex", shown = false) }
            assertEquals(listOf("The duo emote was cancelled"), notices)
            DuoEmoteState.cancelDeparted(System.currentTimeMillis() + 2_000L)
            assertEquals(0, stops)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-stayed", SharedEmoteResult.Expired))
            DuoEmoteState.followUp(current, emptyMap())
            DuoEmoteState.cancelDeparted(System.currentTimeMillis() + 4_000L)
            assertEquals(1, stops)
            assertTrue(DuoEmoteState.cancelledStart(876_663, partner))
            assertFalse(DuoEmoteState.hasOpenRequests())
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-left", SharedEmoteResult.Cancelled))
            assertTrue(DuoEmoteState.followUp(emptyMap(), emptyMap()).isEmpty())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `an ask still in flight to a partner who left is cancelled and its ack dropped`() {
        val local = UUID(68L, 68L)
        val partner = UUID(69L, 69L)
        val emoteId = 876_665
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.accountProvider = { local }
        try {
            DuoEmoteState.noteAsk(partner, emoteId, 81L)
            DuoEmoteState.forget(partner)
            assertEquals(1, stops)
            assertFalse(DuoEmoteState.hasSentAsks())
            DuoEmoteState.noteAsk(partner, emoteId, 82L)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-before-leaving", partner.toString(), emoteId, Long.MAX_VALUE))
            assertTrue(DuoEmoteState.outgoingRequests().isEmpty())
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-after-return", partner.toString(), emoteId, Long.MAX_VALUE))
            assertEquals(listOf("dr-after-return"), DuoEmoteState.outgoingRequests().map { it.requestId })
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a late ack for a partner who left stays hidden and is cancelled once nothing else is open`() {
        val local = UUID(70L, 70L)
        val partner = UUID(71L, 71L)
        val other = UUID(72L, 72L)
        val emoteId = 876_666
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.accountProvider = { local }
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-other", other.toString(), 876_667, Long.MAX_VALUE))
            DuoEmoteState.noteAsk(partner, emoteId, 83L)
            DuoEmoteState.forget(partner)
            assertEquals(0, stops)
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-late", partner.toString(), emoteId, Long.MAX_VALUE))
            assertEquals(0, stops)
            assertEquals(listOf("dr-other"), DuoEmoteState.outgoingRequests().map { it.requestId })
            assertEquals(listOf("dr-other"), DuoEmoteState.unresolvedRequests().map { it.requestId })
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-late", SharedEmoteResult.Accepted))
            assertTrue(DuoEmoteState.incomingRequests().none { it.accepted } && DuoEmoteState.outgoingRequests().none { it.accepted })
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteResolved("dr-other", SharedEmoteResult.Declined))
            DuoEmoteState.cancelDeparted(System.currentTimeMillis() + 2_000L)
            assertEquals(1, stops)
            assertFalse(DuoEmoteState.hasOpenRequests())
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `a late start with a partner who left is stopped`() {
        val local = UUID(73L, 73L)
        val partner = UUID(74L, 74L)
        val other = UUID(75L, 75L)
        val emoteId = 876_668
        var stops = 0
        DuoEmoteState.stopSender = { stops++; Result.success(Unit) }
        DuoEmoteState.accountProvider = { local }
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-leaving", partner.toString(), emoteId, Long.MAX_VALUE))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequestSent("dr-staying", other.toString(), 876_669, Long.MAX_VALUE))
            DuoEmoteState.forget(partner)
            assertEquals(0, stops)
            val session = SharedEmote(emoteId, listOf(local.toString(), partner.toString()), EmoteAnchor(0.0, 0.0, 0.0, 0f), 0L)
            DuoEmoteState.noteDepartedStart(session, listOf(local, partner))
            DuoEmoteState.applySnapshot(emptyList(), emptyMap(), emptyMap(), emptyMap(), mapOf(local.toString() to session))
            DuoEmoteState.cancelDeparted(System.currentTimeMillis() + 2_000L)
            assertEquals(1, stops)
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `overlapping invitations each keep their own time`() {
        val now = EmoteServerClock.now()
        allowRequests()
        try {
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequested("dr-second", UUID(58L, 58L).toString(), 876_655, now + 9_000L))
            DuoEmoteState.handle(ClientboundPacket.SharedEmoteRequested("dr-first", UUID(59L, 59L).toString(), 876_656, now + 8_000L))
            val prompts = DuoEmoteState.incomingPrompts()
            assertEquals(listOf("dr-first", "dr-second"), prompts.map { it.requestId })
            assertEquals(listOf(now + 8_000L, now + 9_000L), prompts.map { it.expiresAt })
            DuoEmoteState.responseSender = { Result.success(Unit) }
            assertTrue(DuoEmoteState.respond("dr-first", accept = false).isSuccess)
            assertEquals(listOf("dr-second"), DuoEmoteState.incomingPrompts().map { it.requestId })
        } finally {
            DuoEmoteState.reset()
        }
    }

    @Test
    fun `waiting invitations are listed under the current prompt`() {
        assertNull(othersWaiting(emptyList(), 5))
        assertEquals("Sam also asked you (7s)", othersWaiting(listOf("Sam"), 7))
        assertEquals("Sam and 1 other also asked you (7s)", othersWaiting(listOf("Sam", "Jo"), 7))
        assertEquals("Sam and 2 others also asked you (7s)", othersWaiting(listOf("Sam", "Jo", "Kim"), 7))
    }
}
