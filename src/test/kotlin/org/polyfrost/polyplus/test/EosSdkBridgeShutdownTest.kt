package org.polyfrost.polyplus.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.polyfrost.polyplus.client.network.eos.EosSdkBridgeImpl
import java.util.concurrent.TimeUnit

class EosSdkBridgeShutdownTest {
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `a call made after shutdown fails instead of waiting for a tick thread that is gone`() {
        val bridge = EosSdkBridgeImpl()
        bridge.shutdown()

        val result = runBlocking { bridge.queryNatType() }

        assertTrue(result.isFailure, "queryNatType answered after shutdown: $result")
        assertTrue(
            result.exceptionOrNull()?.message?.contains("shutting down") == true,
            "expected the call to be refused, but it failed with ${result.exceptionOrNull()}",
        )
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `a bridge that was shut down refuses to start`() {
        val bridge = EosSdkBridgeImpl()
        bridge.shutdown()

        assertFalse(bridge.initialize(), "a shut-down bridge reported itself as usable")
    }
}
