package org.polyfrost.polyplus.test

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.http.MinecraftLoginGate

class MinecraftLoginGateTest {
    private val noFallthrough = 60_000L

    private suspend fun authorizeWithin(millis: Long): String? =
        withTimeoutOrNull(millis) { MinecraftLoginGate.whileNotLoggingIn(noFallthrough) { "authorized" } }

    @Test
    fun `the gate serialises the game's login against our authorization`() = runBlocking {
        assertEquals("authorized", authorizeWithin(1_000L), "an idle gate must not block")

        val outer = MinecraftLoginGate.begin()
        val inner = MinecraftLoginGate.begin()
        assertTrue(outer && inner, "a login nested on the same thread must not deadlock on itself")

        MinecraftLoginGate.end(inner)
        assertNull(authorizeWithin(200L), "only the outermost end may hand the gate over")

        assertEquals(
            "authorized",
            withTimeoutOrNull(2_000L) { MinecraftLoginGate.whileNotLoggingIn(200L) { "authorized" } },
            "a login that never ends must not strand authorization",
        )

        MinecraftLoginGate.end(outer)
        assertNull(authorizeWithin(200L), "must stay shut until the server is done with its hasJoined")

        MinecraftLoginGate.loginSettled()
        assertEquals("authorized", authorizeWithin(1_000L), "the outcome signal must reopen the gate")
    }

    @Test
    fun `a late outcome signal cannot leak a second permit`() = runBlocking {
        MinecraftLoginGate.loginSettled()
        MinecraftLoginGate.loginSettled()

        val held = MinecraftLoginGate.begin()
        assertTrue(held, "the gate must still be a single permit")
        assertNull(authorizeWithin(200L), "a stray signal must not have opened a second lane")
        MinecraftLoginGate.end(held)
        MinecraftLoginGate.loginSettled()
    }
}
