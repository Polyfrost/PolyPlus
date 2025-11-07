package org.polyfrost.polyplus.client.network.websocket

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig

object PolyConnection {
    private val LOGGER = LogManager.getLogger()

    private var job: Job? = null
    private var session: DefaultClientWebSocketSession? = null
    private val _outgoing = Channel<String>(Channel.Factory.UNLIMITED)

    val isConnected: Boolean
        get() = session != null

    fun initialize() {
        start() // Just cold start and set up
    }

    /**
     * Reconnects the WebSocket connection. Best for when the connection is lost, or we'd like to switch servers.
     */
    fun reconnect() {
        close()
        start()
    }

    fun close() {
        job?.cancel()
        job = null
        session = null
    }

    fun sendMessage(message: String): Result<Unit> {
        if (session == null) {
            return Result.failure(IllegalStateException("WebSocket is not connected"))
        }

        PolyPlusClient.SCOPE.launch {
            try {
                _outgoing.send(message)
            } catch (e: Exception) {
                LOGGER.error("Failed to enqueue WebSocket message", e)
            }
        }

        return Result.success(Unit)
    }

    fun sendPacket(packet: ServerboundPacket): Result<Unit> {
        return sendMessage(packet.string())
    }

    private fun start() {
        job = PolyPlusClient.SCOPE.launch {
            try {
                val apiUrl = PolyPlusConfig.apiUrl.toString()
                    .replace("http", "ws")
                    .removeSuffix("/")
                PolyPlusClient.HTTP.webSocket("${apiUrl}/websocket") {
                    session = this

                    val sender = launch {
                        for (message in _outgoing) {
                            try {
                                send(Frame.Text(message))
                            } catch (e: Exception) {
                                LOGGER.error("Failed to send WebSocket message", e)
                            }
                        }
                    }

                    for (frame in incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        process(this, text)
                    }

                    sender.cancel()
                }
            } catch (e: Exception) {
                LOGGER.error("WebSocket connection failed", e)
            } finally {
                session = null
            }
        }
    }

    private fun process(scope: CoroutineScope, message: String) {
        println("Received message: $message")
        val packet = PolyPlusClient.JSON.decodeFromString<ClientboundPacket>(message)
        println("Decoded packet: $packet")
    }

    private inline fun <reified T : ServerboundPacket> T.string(): String {
        return PolyPlusClient.JSON.encodeToString(serializer(), this)
    }
}