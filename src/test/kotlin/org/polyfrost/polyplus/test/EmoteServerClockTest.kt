package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emotes.EmoteServerClock

class EmoteServerClockTest {
    @Test
    fun `uses both server stamps when the receive time is known`() {
        assertEquals(1000L, EmoteServerClock.offsetFor(serverMs = 1450, sentMs = 0, serverReceivedMs = 1050, receivedMs = 500))
    }

    @Test
    fun `ignores slow server processing when the receive time is known`() {
        assertEquals(1000L, EmoteServerClock.offsetFor(serverMs = 1800, sentMs = 0, serverReceivedMs = 1100, receivedMs = 900))
    }

    @Test
    fun `falls back to the round trip midpoint without a receive time`() {
        assertEquals(1250L, EmoteServerClock.offsetFor(serverMs = 1500, sentMs = 0, serverReceivedMs = null, receivedMs = 500))
    }
}
