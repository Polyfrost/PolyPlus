package org.polyfrost.polyplus.test

import com.sun.management.ThreadMXBean
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emotes.duo.DuoEmoteState
import java.lang.management.ManagementFactory
import java.util.UUID

class DuoIdleAllocationTest {
    private val account = DuoEmoteState.accountProvider

    @AfterEach
    fun restore() {
        DuoEmoteState.accountProvider = account
        DuoEmoteState.reset()
    }

    @Test
    fun `idle duo emote polling allocates nothing`() {
        DuoEmoteState.accountProvider = { UUID(0L, 1L) }
        DuoEmoteState.reset()
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val id = Thread.currentThread().id
        repeat(WARMUP) { poll() }
        val before = threads.getThreadAllocatedBytes(id)
        repeat(ROUNDS) { poll() }
        val allocated = threads.getThreadAllocatedBytes(id) - before
        assertTrue(allocated <= ROUNDS, "allocated $allocated bytes over $ROUNDS idle polls")
    }

    private fun poll() {
        DuoEmoteState.unresolvedRequests()
        DuoEmoteState.incomingPrompts()
        DuoEmoteState.incomingRequests()
        DuoEmoteState.outgoingRequests()
        DuoEmoteState.cancelDeparted(0L)
        DuoEmoteState.hasRequests()
        DuoEmoteState.hasEndings()
    }

    private companion object {
        const val WARMUP = 20_000
        const val ROUNDS = 10_000
    }
}
