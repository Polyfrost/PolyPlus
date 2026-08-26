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
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import kotlin.random.Random

object PolyConnection {
    private val LOGGER = LogManager.getLogger()

    private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
    private const val MAX_RECONNECT_DELAY_MS = 60_000L

    private const val MAX_RECONNECT_ATTEMPTS = 12

    private const val TRANSIENT_FAILURES_BEFORE_NOTIFYING = 3

    private const val NOTIFY_AFTER_OUTAGE_MS = 15_000L

    private val HANDSHAKE_STATUS = Regex("expected status code 101 but was (\\d{3})")

    private var connectionCallback: (() -> Unit)? = null
    private var job: Job? = null
    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private val _outgoing = Channel<String>(Channel.Factory.UNLIMITED)

    @Volatile
    private var closing = false

    @Volatile
    private var disconnectNotified = false

    @Volatile
    private var disconnectedSinceMs = 0L

    @Volatile
    private var handshakeSucceeded = false

    val isConnected: Boolean
        get() = session != null

    fun initialize(callback: (() -> Unit)? = null) {
        this.connectionCallback = callback
        start()
    }

    fun applyConsent() {
        if (PrivacyConsent.allowsOnlineServices()) {
            if (job == null) start()
        } else {
            close()
        }
    }

    fun reconnect() {
        close()
        start()
    }

    fun close() {
        closing = true
        disconnectNotified = false
        disconnectedSinceMs = 0L
        job?.cancel()
        job = null
        session = null
    }

    fun sendMessage(message: String): Result<Unit> {
        if (!PrivacyConsent.allowsOnlineServices()) {
            return Result.failure(IllegalStateException("PolyPlus online features are disabled"))
        }

        if (session == null) {
            return Result.failure(IllegalStateException("WebSocket is not connected"))
        }

        val result = _outgoing.trySend(message)
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            if (error != null) {
                LOGGER.error("Failed to enqueue WebSocket message", error)
            }
            return Result.failure(error ?: IllegalStateException("WebSocket outgoing queue rejected message"))
        }
        return Result.success(Unit)
    }

    fun sendPacket(packet: ServerboundPacket): Result<Unit> {
        return sendMessage(packet.string())
    }

    private fun start() {
        if (!PrivacyConsent.allowsOnlineServices()) {
            LOGGER.info("PolyPlus WebSocket disabled: the Terms of Service and Privacy Policy were not accepted.")
            return
        }

        job?.let { existing ->
            existing.cancel()
        }

        closing = false
        job = PolyPlusClient.SCOPE.launch {
            var attempt = 0
            var tokenRefreshed = false
            var transientFailures = 0
            while (isActive) {
                handshakeSucceeded = false
                try {
                    connectOnce()
                    // No throw means the server closed cleanly so treat it as a drop and
                    // reconnect unless we asked to close
                    if (closing || !isActive) break
                    LOGGER.warn("PolyPlus WebSocket closed by server.")
                    notifyDisconnected(null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (closing || !isActive) break
                    if (isAuthFailure(e)) {
                        if (tokenRefreshed) {
                            LOGGER.warn("PolyPlus WebSocket still rejected (401) after a token refresh.")
                        } else {
                            LOGGER.warn("PolyPlus WebSocket authentication failed (401); refreshing token before retry.")
                            tokenRefreshed = true
                            runCatching { PolyAuthorization.reset() }
                        }
                        notifyDisconnected(null)
                    } else if (isTransientHandshakeFailure(e)) {
                        transientFailures++
                        LOGGER.debug("PolyPlus WebSocket handshake failed ({}); retrying.", e.message)
                        if (transientFailures >= TRANSIENT_FAILURES_BEFORE_NOTIFYING) {
                            notifyDisconnected(null)
                        }
                    } else {
                        LOGGER.error("PolyPlus WebSocket connection failed", e)
                        notifyDisconnected(e)
                    }
                } finally {
                    session = null
                }

                if (handshakeSucceeded) {
                    attempt = 0
                    tokenRefreshed = false
                    transientFailures = 0
                }

                attempt++
                if (attempt >= MAX_RECONNECT_ATTEMPTS) {
                    LOGGER.error("Giving up on the PolyPlus WebSocket after {} failed attempts.", attempt)
                    notifyGaveUp()
                    break
                }

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
            handshakeSucceeded = true
            notifyReconnected()

            val sender = launch {
                for (message in _outgoing) {
                    try {
                        send(Frame.Text(message))
                    } catch (e: Exception) {
                        LOGGER.error("Failed to send WebSocket message", e)
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
        return generateSequence(error) { it.cause }.any { cause ->
            val message = cause.message ?: return@any false
            message.contains("401") || message.contains("Unauthorized", ignoreCase = true)
        }
    }

    internal fun isTransientHandshakeFailure(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            val status = cause.message
                ?.let { HANDSHAKE_STATUS.find(it) }
                ?.groupValues?.getOrNull(1)
                ?.toIntOrNull()
            if (status != null && status >= 500) return true
            if (cause is IOException || cause is UnresolvedAddressException) return true
            val next = cause.cause
            if (next === cause) break
            cause = next
        }
        return false
    }

    internal fun reconnectDelay(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 30)
        val delayMs = INITIAL_RECONNECT_DELAY_MS shl shift
        val capped = if (delayMs <= 0L) MAX_RECONNECT_DELAY_MS else delayMs.coerceAtMost(MAX_RECONNECT_DELAY_MS)
        val half = capped / 2
        return half + Random.nextLong(half + 1)
    }

    private fun notifyDisconnected(error: Exception?) {
        if (disconnectNotified) return
        val now = System.currentTimeMillis()
        if (disconnectedSinceMs == 0L) disconnectedSinceMs = now
        if (!outageIsWorthNotifying(disconnectedSinceMs, now)) return
        disconnectNotified = true
        val reason = error?.message?.let { ": $it" } ?: "."
        runCatching {
            Notifications.error("PolyPlus", "Lost connection to PolyPlus$reason Reconnecting...")
        }.onFailure { LOGGER.error("Failed to show disconnect notification", it) }
    }

    internal fun outageIsWorthNotifying(since: Long, now: Long): Boolean {
        return now - since >= NOTIFY_AFTER_OUTAGE_MS
    }

    private fun notifyGaveUp() {
        disconnectNotified = true
        disconnectedSinceMs = 0L
        runCatching {
            Notifications.error(
                "PolyPlus",
                "Could not reconnect to PolyPlus. Run /polyplus refresh or restart the game to retry.",
            )
        }.onFailure { LOGGER.error("Failed to show reconnect-failed notification", it) }
    }

    private fun notifyReconnected() {
        disconnectedSinceMs = 0L
        if (!disconnectNotified) return
        disconnectNotified = false
        runCatching {
            Notifications.success("PolyPlus", "Reconnected to PolyPlus.")
        }.onFailure { LOGGER.error("Failed to show reconnect notification", it) }
    }

    private fun process(scope: CoroutineScope, message: String) {
        try {
            val packet = PolyPlusClient.JSON.decodeFromString<ClientboundPacket>(message)
            if (packet is ClientboundPacket.Error) {
                LOGGER.error("Error packet received: ${packet.message}")
            }

            EventManager.INSTANCE.post(WebSocketMessage(packet))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOGGER.error("Failed to process WebSocket message: $message", e)
        }
    }

    private inline fun <reified T : ServerboundPacket> T.string(): String {
        return PolyPlusClient.JSON.encodeToString(ServerboundPacket.serializer(), this)
    }
}
