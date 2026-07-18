package org.polyfrost.polyplus.client.network.websocket

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.PolyAuthorization
import org.polyfrost.polyplus.events.WebSocketMessage

object PolyConnection {
    private val LOGGER = LogManager.getLogger()

    private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
    private const val MAX_RECONNECT_DELAY_MS = 60_000L

    private var connectionCallback: (() -> Unit)? = null
    private var job: Job? = null
    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private val _outgoing = Channel<String>(Channel.Factory.UNLIMITED)

    @Volatile
    private var closing = false

    @Volatile
    private var disconnectNotified = false

    val isConnected: Boolean
        get() = session != null

    fun initialize(callback: (() -> Unit)? = null) {
        this.connectionCallback = callback
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
        closing = true
        job?.cancel()
        job = null
        session = null
    }

    fun sendMessage(message: String): Result<Unit> {
        if (session == null) {
            return Result.failure(IllegalStateException("WebSocket is not connected"))
        }

        val result = _outgoing.trySend(message)
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            if (error != null) {
                LOGGER.error("Failed to enqueue WebSocket message", error)
                org.polyfrost.polyplus.client.PolyPlusSentry.capture(error)
            }
            return Result.failure(error ?: IllegalStateException("WebSocket outgoing queue rejected message"))
        }
        return Result.success(Unit)
    }

    fun sendPacket(packet: ServerboundPacket): Result<Unit> {
        return sendMessage(packet.string())
    }

    private fun start() {
        closing = false
        job = PolyPlusClient.SCOPE.launch {
            var attempt = 0
            while (isActive) {
                try {
                    connectOnce()
                    // The session ended without throwing: the server closed the
                    // socket cleanly. Treat it as a drop and reconnect unless we
                    // asked to close.
                    if (closing || !isActive) break
                    LOGGER.warn("PolyPlus WebSocket closed by server.")
                    notifyDisconnected(null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (closing || !isActive) break
                    if (isAuthFailure(e)) {
                        LOGGER.warn("PolyPlus WebSocket authentication failed (401); refreshing token before retry.")
                        runCatching { PolyAuthorization.reset() }
                        notifyDisconnected(null)
                    } else {
                        LOGGER.error("PolyPlus WebSocket connection failed", e)
                        org.polyfrost.polyplus.client.PolyPlusSentry.capture(e)
                        notifyDisconnected(e)
                    }
                } finally {
                    session = null
                }

                attempt++
                val backoff = reconnectDelay(attempt)
                LOGGER.info("Reconnecting to PolyPlus WebSocket in {} ms (attempt {}).", backoff, attempt)
                delay(backoff)
            }
        }
    }

    private suspend fun connectOnce() {
        val apiUrl = PolyPlusConfig.apiUrl.toString()
            .replace("http", "ws")
            .removeSuffix("/")
        val token = PolyAuthorization.current()
        PolyPlusClient.HTTP.webSocket("${apiUrl}/websocket", request = {
            bearerAuth(token)
        }) {
            session = this
            notifyReconnected()

            val sender = launch {
                for (message in _outgoing) {
                    try {
                        send(Frame.Text(message))
                    } catch (e: Exception) {
                        LOGGER.error("Failed to send WebSocket message", e)
                        org.polyfrost.polyplus.client.PolyPlusSentry.capture(e)
                    }
                }
            }

            connectionCallback?.invoke()
            for (frame in incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                process(this, text)
            }

            sender.cancel()
        }
    }

    private fun isAuthFailure(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }.any { it.message?.contains("401") == true }
    }

    private fun reconnectDelay(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 30)
        val delayMs = INITIAL_RECONNECT_DELAY_MS shl shift
        return if (delayMs <= 0L) MAX_RECONNECT_DELAY_MS else delayMs.coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    private fun notifyDisconnected(error: Exception?) {
        if (disconnectNotified) return
        disconnectNotified = true
        val reason = error?.message?.let { ": $it" } ?: "."
        runCatching {
            Notifications.error("PolyPlus", "Lost connection to PolyPlus$reason Reconnecting...")
        }.onFailure { LOGGER.error("Failed to show disconnect notification", it) }
    }

    private fun notifyReconnected() {
        if (!disconnectNotified) return
        disconnectNotified = false
        runCatching {
            Notifications.success("PolyPlus", "Reconnected to PolyPlus.")
        }.onFailure { LOGGER.error("Failed to show reconnect notification", it) }
    }

    private fun process(scope: CoroutineScope, message: String) {
        val packet = PolyPlusClient.JSON.decodeFromString<ClientboundPacket>(message)
        if (packet is ClientboundPacket.Error) {
            LOGGER.error("Error packet received: ${packet.message}")
            org.polyfrost.polyplus.client.PolyPlusSentry.captureMessage("Error packet received: ${packet.message}")
        }

        EventManager.INSTANCE.post(WebSocketMessage(packet))
    }

    private inline fun <reified T : ServerboundPacket> T.string(): String {
        return PolyPlusClient.JSON.encodeToString(ServerboundPacket.serializer(), this)
    }
}
