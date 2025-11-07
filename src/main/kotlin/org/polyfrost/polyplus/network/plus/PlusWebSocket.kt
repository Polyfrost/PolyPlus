package org.polyfrost.polyplus.network.plus

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.closeExceptionally
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.PolyPlus
import org.polyfrost.polyplus.client.Config
import org.polyfrost.polyplus.network.plus.responses.WebSocketPacket
import org.polyfrost.polyui.utils.mapToArray

object PlusWebSocket {
    private var session: DefaultClientWebSocketSession? = null
    private val _outgoing = Channel<String>(Channel.UNLIMITED)

    private var callback: (suspend CoroutineScope.(String) -> Unit)? = null

    val isConnected: Boolean
        get() = session != null

    fun start() = PolyPlus.scope.launch {
        try {
            PolyPlus.client.webSocket("${Config.apiUrl.replace("http", "ws")}websocket") {
                session = this

                PolyPlus.logger.info("Connected to PolyPlus WebSocket")

                val sender = launch {
                    for (message in _outgoing) {
                        try {
                            send(Frame.Text(message))
                        } catch (e: Exception) {
                            PolyPlus.logger.warning("Failed to send message over websocket: ${e.message}")
                            return@launch
                        }
                    }
                }

                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    callback?.invoke(this, text)
                }

                sender.cancel()
            }
        } catch (e: Exception) {
            PolyPlus.logger.warning("WebSocket connection failed: ${e.message}")
        } finally {
            session = null
        }
    }

    fun setOnMessage(callback: suspend CoroutineScope.(String) -> Unit) {
        this.callback = callback
    }

    fun send(message: String): Result<Unit> {
        PolyPlus.logger.info("Tryna send ${message}")
        if (session == null) return Result.failure(IllegalStateException("WebSocket is not connected"))

        PolyPlus.scope.launch {
            try {
                _outgoing.send(message)
            } catch (e: Exception) {
                PolyPlus.logger.warning("Failed to send message to the send channel: ${e.message}")
                session?.closeExceptionally(e)
            }
        }

        return Result.success(Unit)
    }

    fun getActiveCosmetics(vararg players: String): Result<Unit> {
        val message = WebSocketPacket.ServerBound.Packet.GetActiveCosmetics(players.toList())
        val jsonMessage = PolyPlus.json.encodeToString(WebSocketPacket.ServerBound.Packet.serializer(), message)
        return send(jsonMessage)
    }
}