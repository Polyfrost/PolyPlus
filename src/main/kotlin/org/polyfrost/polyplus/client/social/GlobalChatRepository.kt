package org.polyfrost.polyplus.client.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.GlobalChatApi
import org.polyfrost.polyplus.client.network.http.responses.GlobalChatMessage
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.events.WebSocketMessage
import org.polyfrost.polyplus.utils.EarlyInitializable

object GlobalChatRepository : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()

    private const val MAX_CACHED_MESSAGES = 500

    private val _messages = MutableStateFlow<List<GlobalChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    override fun earlyInitialize() {
        eventHandler<WebSocketMessage> { event ->
            val packet = event.packet as? ClientboundPacket.GlobalChatMessageReceived ?: return@eventHandler
            append(
                GlobalChatMessage(
                    id = packet.messageId,
                    sender = packet.sender,
                    content = packet.content,
                    sentAt = java.time.Instant.now().toString(),
                ),
            )
        }.register()
    }

    fun refreshHistory(limit: Long? = null) = PolyPlusClient.SCOPE.launch {
        GlobalChatApi.history(limit = limit)
            .onSuccess { _messages.value = it.sortedBy { message -> message.id } }
            .onFailure { LOGGER.error("Failed to load Global Chat history", it) }
    }

    fun send(content: String) = PolyPlusClient.SCOPE.launch {
        GlobalChatApi.send(content)
            .onSuccess { append(it) }
            .onFailure {
                LOGGER.error("Failed to send Global Chat message", it)
                SocialErrors.emit("Couldn't send message", it)
            }
    }

    private fun append(message: GlobalChatMessage) {
        val updated = (_messages.value.filterNot { it.id == message.id } + message).sortedBy { it.id }
        _messages.value = if (updated.size > MAX_CACHED_MESSAGES) updated.takeLast(MAX_CACHED_MESSAGES) else updated
    }
}
