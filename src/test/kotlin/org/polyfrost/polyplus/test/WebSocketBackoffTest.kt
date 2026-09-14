package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

class WebSocketBackoffTest {
    private companion object {
        const val INITIAL_MS = 1_000L
        const val MAX_MS = 60_000L
        const val SAMPLES = 200
    }

    private fun handshakeFailure(status: Int) =
        RuntimeException("Handshake exception, expected status code 101 but was $status")

    @Test
    fun `a 5xx handshake is transient, whatever it is wrapped in`() {
        assertTrue(PolyConnection.isTransientHandshakeFailure(handshakeFailure(502)))
        assertTrue(PolyConnection.isTransientHandshakeFailure(handshakeFailure(503)))
        assertTrue(
            PolyConnection.isTransientHandshakeFailure(
                IllegalStateException("websocket failed", handshakeFailure(500)),
            ),
        )
    }

    @Test
    fun `socket-level failures are transient`() {
        assertTrue(
            PolyConnection.isTransientHandshakeFailure(
                EOFException("Failed to parse HTTP response: the server prematurely closed the connection"),
            ),
        )
        assertTrue(PolyConnection.isTransientHandshakeFailure(ConnectException("Connection refused")))
        assertTrue(PolyConnection.isTransientHandshakeFailure(SocketTimeoutException("timeout")))
        assertTrue(PolyConnection.isTransientHandshakeFailure(UnknownHostException("api.polyfrost.org")))
        assertTrue(PolyConnection.isTransientHandshakeFailure(UnresolvedAddressException()))
        assertTrue(
            PolyConnection.isTransientHandshakeFailure(
                IllegalStateException("websocket failed", EOFException()),
            ),
        )
    }

    @Test
    fun `authentication and unrelated failures are not transient`() {
        assertFalse(PolyConnection.isTransientHandshakeFailure(handshakeFailure(401)))
        assertFalse(PolyConnection.isTransientHandshakeFailure(handshakeFailure(404)))
        assertFalse(PolyConnection.isTransientHandshakeFailure(RuntimeException("something else")))
        assertFalse(PolyConnection.isTransientHandshakeFailure(RuntimeException()))
    }

    @Test
    fun `backoff grows, stays within the cap, and never returns zero`() {
        for (attempt in 1..40) {
            val ceiling = (INITIAL_MS shl (attempt - 1).coerceIn(0, 30))
                .takeIf { it > 0L }?.coerceAtMost(MAX_MS) ?: MAX_MS
            repeat(SAMPLES) {
                val delay = PolyConnection.reconnectDelay(attempt)
                assertTrue(delay > 0L, "attempt $attempt produced $delay")
                assertTrue(delay <= ceiling, "attempt $attempt produced $delay, over $ceiling")
                assertTrue(delay >= ceiling / 2, "attempt $attempt produced $delay, under ${ceiling / 2}")
            }
        }
    }

    @Test
    fun `short outages are not worth a notification`() {
        val start = 1_000_000L
        assertFalse(PolyConnection.outageIsWorthNotifying(start, start))
        assertFalse(PolyConnection.outageIsWorthNotifying(start, start + 14_999L))
        assertTrue(PolyConnection.outageIsWorthNotifying(start, start + 15_000L))
        assertTrue(PolyConnection.outageIsWorthNotifying(start, start + 60_000L))
    }

    @Test
    fun `backoff is jittered rather than fixed`() {
        val seen = (1..SAMPLES).map { PolyConnection.reconnectDelay(10) }.toSet()
        assertTrue(seen.size > 1, "expected jitter, every attempt returned ${seen.first()}")
    }
}
