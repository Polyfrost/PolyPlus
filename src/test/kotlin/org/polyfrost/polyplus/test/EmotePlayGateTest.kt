package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emotes.EmotePlayGate

class EmotePlayGateTest {
    @Test
    fun `a play queued before a stop is never sent`() {
        val gate = EmotePlayGate()
        val ticket = gate.ticket()
        var sent = 0
        gate.cancel { }
        assertFalse(gate.open(ticket))
        assertNull(gate.send(ticket) { ++sent })
        assertEquals(0, sent)
    }

    @Test
    fun `a play picked after the stop still goes out`() {
        val gate = EmotePlayGate()
        gate.cancel { }
        val ticket = gate.ticket()
        assertTrue(gate.open(ticket))
        assertEquals(1, gate.send(ticket) { 1 })
        assertEquals(2, gate.send(null) { 2 })
    }

    @Test
    fun `a stop that lands after the send sees it`() {
        val gate = EmotePlayGate()
        val ticket = gate.ticket()
        var sent = false
        gate.send(ticket) { sent = true }
        assertTrue(gate.cancel { sent })
    }
}
