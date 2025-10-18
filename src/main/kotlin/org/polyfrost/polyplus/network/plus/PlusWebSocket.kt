package org.polyfrost.polyplus.network.plus

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.PolyPlus

object PlusWebSocket {
    private var session: DefaultClientWebSocketSession? = null
    private val _outgoing = Channel<String>(Channel.UNLIMITED)

    private var callback: (suspend CoroutineScope.(String) -> Unit)? = null

    val isConnected: Boolean
        get() = session != null

    fun start() = PolyPlus.scope.launch {
        try {
            PolyPlus.client.webSocket {
                session = this

                val sender = launch {
                    for (message in _outgoing) {
                        send(Frame.Text(message))
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
        if (session == null) return Result.failure(IllegalStateException("WebSocket is not connected"))

        PolyPlus.scope.launch {
            _outgoing.send(message)
        }

        return Result.success(Unit)
    }
}