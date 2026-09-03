package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.eos.EosLogThrottle

class EosLogThrottleTest {
    private companion object {
        const val ATTEMPT = "0x7fc46ca18850: request failed, libcurl error: 7 (Couldn't connect to server)"
        const val OTHER = "SDK Config Platform Update Request Failed, Result Code: EOS_NoConnection"
    }

    @Test
    fun `retries of the same failure collapse into one key`() {
        assertEquals(
            EosLogThrottle.normalize(ATTEMPT),
            EosLogThrottle.normalize("0x7fc46f389680: request failed, libcurl error: 7 (Couldn't connect to server)"),
        )
        assertEquals(
            EosLogThrottle.normalize("Failed to connect to api.epicgames.dev port 443 after 15 ms"),
            EosLogThrottle.normalize("Failed to connect to api.epicgames.dev port 443 after 55 ms"),
        )
    }

    @Test
    fun `a different error code is a different failure, not a repeat of the first`() {
        assertNotEquals(
            EosLogThrottle.normalize("0x7fc46ca18850: request failed, HTTP status 429"),
            EosLogThrottle.normalize("0x7fc46f389680: request failed, HTTP status 500"),
        )
    }

    @Test
    fun `the first few repeats are logged, the rest are suppressed until the interval passes`() {
        val throttle = EosLogThrottle(burst = 3, firstIntervalMs = 1_000, maxIntervalMs = 4_000)

        repeat(3) { assertEquals(0, throttle.onMessage(ATTEMPT, 0)) }
        assertNull(throttle.onMessage(ATTEMPT, 0))
        assertNull(throttle.onMessage(ATTEMPT, 999))

        assertEquals(2, throttle.onMessage(ATTEMPT, 1_000))
        assertNull(throttle.onMessage(ATTEMPT, 1_001))
    }

    @Test
    fun `the interval backs off so a permanently broken connection goes quiet`() {
        val throttle = EosLogThrottle(burst = 1, firstIntervalMs = 1_000, maxIntervalMs = 4_000)

        assertEquals(0, throttle.onMessage(ATTEMPT, 0))
        assertEquals(0, throttle.onMessage(ATTEMPT, 1_000))
        assertNull(throttle.onMessage(ATTEMPT, 2_999))
        assertEquals(1, throttle.onMessage(ATTEMPT, 3_000))
        assertNull(throttle.onMessage(ATTEMPT, 6_999))
        assertEquals(1, throttle.onMessage(ATTEMPT, 7_000))
        assertNull(throttle.onMessage(ATTEMPT, 10_999))
        assertEquals(1, throttle.onMessage(ATTEMPT, 11_000))
    }

    @Test
    fun `a failure that clears and comes back much later gets its full burst again`() {
        val throttle = EosLogThrottle(burst = 2, firstIntervalMs = 1_000, maxIntervalMs = 4_000)

        assertEquals(0, throttle.onMessage(ATTEMPT, 0))
        assertEquals(0, throttle.onMessage(ATTEMPT, 0))
        assertNull(throttle.onMessage(ATTEMPT, 0))
        assertEquals(1, throttle.onMessage(ATTEMPT, 1_000))
        assertNull(throttle.onMessage(ATTEMPT, 1_001))

        val backAgain = 1_000 + 4_000L * EosLogThrottle.QUIET_RESET_FACTOR + 1
        assertEquals(1, throttle.onMessage(ATTEMPT, backAgain))
        assertEquals(0, throttle.onMessage(ATTEMPT, backAgain))
        assertNull(throttle.onMessage(ATTEMPT, backAgain))
    }

    @Test
    fun `different messages are throttled independently`() {
        val throttle = EosLogThrottle(burst = 1, firstIntervalMs = 1_000, maxIntervalMs = 1_000)

        assertEquals(0, throttle.onMessage(ATTEMPT, 0))
        assertNull(throttle.onMessage(ATTEMPT, 0))
        assertEquals(0, throttle.onMessage(OTHER, 0))
    }

    @Test
    fun `two failures alternating keep their own budgets`() {
        val throttle = EosLogThrottle(burst = 2, firstIntervalMs = 1_000, maxIntervalMs = 1_000)

        assertEquals(0, throttle.onMessage(ATTEMPT, 0))
        assertEquals(0, throttle.onMessage(OTHER, 0))
        assertEquals(0, throttle.onMessage(ATTEMPT, 0))
        assertEquals(0, throttle.onMessage(OTHER, 0))
        assertNull(throttle.onMessage(ATTEMPT, 0))
        assertNull(throttle.onMessage(OTHER, 0))
    }
}
