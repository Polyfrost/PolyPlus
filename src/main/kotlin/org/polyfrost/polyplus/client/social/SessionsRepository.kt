package org.polyfrost.polyplus.client.social

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.SessionsApi
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.http.responses.SessionResponse
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.events.WebSocketMessage
import org.polyfrost.polyplus.utils.EarlyInitializable

object SessionsRepository : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()

    private val _incomingInvites = MutableStateFlow<List<SessionInvite>>(emptyList())
    val incomingInvites = _incomingInvites.asStateFlow()

    private val _acceptedInvites = MutableSharedFlow<SessionInvite>(extraBufferCapacity = 8)

    val acceptedInvites = _acceptedInvites.asSharedFlow()

    override fun earlyInitialize() {
        eventHandler<WebSocketMessage> { event ->
            when (event.packet) {
                is ClientboundPacket.SessionInviteReceived,
                is ClientboundPacket.SessionInviteUpdated,
                -> {
                    refreshIncoming()
                }
                else -> {}
            }
            Unit
        }.register()
    }

    fun refreshIncoming() = PolyPlusClient.SCOPE.launch {
        SessionsApi.incomingInvites()
            .onSuccess { _incomingInvites.value = it }
            .onFailure { LOGGER.error("Failed to refresh incoming session invites", it) }
    }

    fun host(eosSessionId: String? = null, onResult: (Result<SessionResponse>) -> Unit = {}) = PolyPlusClient.SCOPE.launch {
        val result = SessionsApi.create(eosSessionId)
        result.onFailure {
            LOGGER.error("Failed to host a session", it)
            SocialErrors.emit("Couldn't start hosting", it)
        }
        onResult(result)
    }

    fun close(sessionId: String) = PolyPlusClient.SCOPE.launch {
        SessionsApi.close(sessionId).onFailure { LOGGER.error("Failed to close session $sessionId", it) }
    }

    fun invite(sessionId: String, player: String) = PolyPlusClient.SCOPE.launch {
        SessionsApi.invite(sessionId, player).onFailure {
            LOGGER.error("Failed to invite $player to session $sessionId", it)
            SocialErrors.emit("Couldn't send session invite", it)
        }
    }

    fun accept(invite: SessionInvite) = PolyPlusClient.SCOPE.launch {
        SessionsApi.acceptInvite(invite.id)
            .onSuccess {
                refreshIncoming()
                _acceptedInvites.emit(invite)
            }
            .onFailure {
                LOGGER.error("Failed to accept session invite ${invite.id}", it)
                SocialErrors.emit("Couldn't accept session invite", it)
            }
    }

    fun decline(invite: SessionInvite) = PolyPlusClient.SCOPE.launch {
        SessionsApi.declineInvite(invite.id).onSuccess { refreshIncoming() }
            .onFailure {
                LOGGER.error("Failed to decline session invite ${invite.id}", it)
                SocialErrors.emit("Couldn't decline session invite", it)
            }
    }
}
