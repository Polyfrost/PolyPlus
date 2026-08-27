package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.eos.EosTickHealth

class EosTickHealthTest {
    private companion object {
        const val NOW = 1_000_000L
        val OVER = EosTickHealth.STALL_THRESHOLD_MS + 1
    }

    @Test
    fun `a long gap since the last tick is a stall`() {
        assertTrue(EosTickHealth.isStalled(ticking = true, lastTickMs = NOW - OVER, nowMs = NOW))
    }

    @Test
    fun `a tick within the threshold is healthy`() {
        assertFalse(EosTickHealth.isStalled(ticking = true, lastTickMs = NOW, nowMs = NOW))
        assertFalse(
            EosTickHealth.isStalled(
                ticking = true,
                lastTickMs = NOW - EosTickHealth.STALL_THRESHOLD_MS,
                nowMs = NOW,
            ),
        )
    }

    @Test
    fun `a loop that never started is not a stall`() {
        assertFalse(EosTickHealth.isStalled(ticking = true, lastTickMs = 0L, nowMs = NOW))
        assertFalse(EosTickHealth.isStalled(ticking = false, lastTickMs = NOW - OVER, nowMs = NOW))
    }

    @Test
    fun `a look taken after the watchdog was away itself proves nothing`() {
        assertFalse(EosTickHealth.isObservationTrustworthy(lastCheckMs = 0L, nowMs = NOW))
        assertFalse(
            EosTickHealth.isObservationTrustworthy(
                lastCheckMs = NOW - EosTickHealth.OBSERVER_GAP_TOLERANCE_MS - 1,
                nowMs = NOW,
            ),
        )
    }

    @Test
    fun `a look taken while the client keeps ticking is trustworthy`() {
        assertTrue(EosTickHealth.isObservationTrustworthy(lastCheckMs = NOW - 50L, nowMs = NOW))
        assertTrue(
            EosTickHealth.isObservationTrustworthy(
                lastCheckMs = NOW - EosTickHealth.OBSERVER_GAP_TOLERANCE_MS,
                nowMs = NOW,
            ),
        )
    }
}
