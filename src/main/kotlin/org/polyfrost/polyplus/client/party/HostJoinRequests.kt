package org.polyfrost.polyplus.client.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.notifications.v1.Notification
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusPartyConfig
import org.polyfrost.polyplus.client.network.http.PartyApi
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.events.WebSocketMessage
import java.time.Instant

object HostJoinRequests {
    private val LOGGER = LogManager.getLogger()

    private const val FALLBACK_MS = 60_000L
    private const val MIN_NOTICE_MS = 1_000L

    class Request(val id: String, val sessionId: String, val player: String, val expiresAt: Long) {
        var answering by mutableStateOf(false)
        internal var notice: Notification? = null
        val name: String get() = PlayerNamesRepository.nameOr(player)
    }

    val pending = mutableStateListOf<Request>()

    fun earlyInitialize() {
        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.HostedJoinRequested -> ClientPlatform.runOnMain { onRequested(packet) }
                is ClientboundPacket.HostedJoinResolved -> ClientPlatform.runOnMain { remove(packet.requestId) }
                else -> Unit
            }
        }
    }

    private fun onRequested(packet: ClientboundPacket.HostedJoinRequested) {
        PlayerNamesRepository.resolve(listOf(packet.player))
        when (PolyPlusPartyConfig.partyHostJoin) {
            HostJoinPolicy.ALWAYS -> send(packet.requestId, true)
            HostJoinPolicy.NEVER -> send(packet.requestId, false)
            HostJoinPolicy.ASK -> ask(packet)
        }
    }

    private fun ask(packet: ClientboundPacket.HostedJoinRequested) {
        if (pending.any { it.id == packet.requestId }) return
        val now = System.currentTimeMillis()
        val expiresAt = runCatching { Instant.parse(packet.expiresAt).toEpochMilli() }.getOrDefault(now + FALLBACK_MS)
        val request = Request(packet.requestId, packet.sessionId, packet.player, expiresAt)
        pending += request
        request.notice = PartyNotices.send(
            "${request.name} wants to join your world",
            (expiresAt - now).coerceAtLeast(MIN_NOTICE_MS).toFloat(),
            "Allow",
            { allow(request.id) },
            "Deny",
            { deny(request.id) },
        )
    }

    fun allow(id: String) = answer(id, true)

    fun deny(id: String) = answer(id, false)

    private fun answer(id: String, allow: Boolean) {
        val request = pending.firstOrNull { it.id == id } ?: return
        if (request.answering) return
        request.answering = true
        send(id, allow) { request.answering = false }
    }

    private fun send(id: String, allow: Boolean, onFailure: () -> Unit = {}) {
        PolyPlusClient.SCOPE.launch {
            val result = if (allow) PartyApi.allowHostedJoin(id) else PartyApi.denyHostedJoin(id)
            ClientPlatform.runOnMain {
                result
                    .onSuccess { remove(id) }
                    .onFailure { error ->
                        LOGGER.warn("Could not answer hosted join request {}", id, error)
                        if (PartyErrors.code(error) == "join_request_not_found") {
                            remove(id)
                        } else {
                            onFailure()
                            SocialErrors.emit(PartyErrors.message(error, "Couldn't answer the join request"))
                        }
                    }
            }
        }
    }

    private fun remove(id: String) {
        pending.filter { it.id == id }.forEach(::retire)
    }

    private fun retire(request: Request) {
        request.notice?.let(PartyNotices::retire)
        request.notice = null
        pending.remove(request)
    }

    fun tick() {
        if (pending.isEmpty()) return
        val now = System.currentTimeMillis()
        pending.filter { now > it.expiresAt }.forEach(::retire)
    }
}
