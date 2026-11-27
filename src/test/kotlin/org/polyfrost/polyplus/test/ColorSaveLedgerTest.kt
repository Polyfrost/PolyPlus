package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.colors.AckSupport
import org.polyfrost.polyplus.client.cosmetics.colors.ColorSaveLedger
import org.polyfrost.polyplus.client.cosmetics.colors.SaveVerdict

class ColorSaveLedgerTest {
    private val fur = mapOf("fur" to 0x123456)
    private val eyes = mapOf("eyes" to 0x654321)

    @Test
    fun `an ack settles only the save with its request id`() {
        val ledger = ColorSaveLedger<String>()
        val first = ledger.open(1, 5, fur) { "first" }
        val second = ledger.open(2, 5, eyes) { "second" }

        assertSame(second, ledger.acknowledge(2))

        assertEquals(AckSupport.Supported, ledger.support)
        assertTrue(ledger.hasPending(5))
        assertSame(first, ledger.acknowledge(1))
        assertFalse(ledger.hasPending(5))
    }

    @Test
    fun `an error with a request id rejects only that save`() {
        val ledger = ColorSaveLedger<String>()
        ledger.open(1, 5, fur) { "first" }
        val second = ledger.open(2, 7, eyes) { "second" }

        assertSame(second, ledger.reject(2))

        assertEquals(AckSupport.Supported, ledger.support)
        assertTrue(ledger.hasPending(5))
        assertFalse(ledger.hasPending(7))
    }

    @Test
    fun `an error for some other request leaves colour saves alone`() {
        val ledger = ColorSaveLedger<String>()
        ledger.open(1, 5, fur) { "first" }

        assertNull(ledger.reject(99))

        assertEquals(AckSupport.Unknown, ledger.support)
        assertTrue(ledger.hasPending(5))
    }

    @Test
    fun `once acks are seen, errors without an id blame nothing`() {
        val ledger = ColorSaveLedger<String>()
        ledger.open(1, 5, fur) { "first" }
        ledger.acknowledge(1)
        val waiting = ledger.open(2, 7, eyes) { "second" }

        assertNull(ledger.uncorrelated("rate_limited", null))

        assertEquals(SaveVerdict.Unconfirmed, ledger.expire(waiting, echoExpected = false))
    }

    @Test
    fun `before acks are known, an echo waits for the timeout and marks the backend as old`() {
        val ledger = ColorSaveLedger<String>()
        val save = ledger.open(1, 5, fur) { "first" }

        assertTrue(ledger.echo(5, fur).isEmpty())
        assertTrue(ledger.hasPending(5))

        assertEquals(SaveVerdict.Saved, ledger.expire(save, echoExpected = true))
        assertEquals(AckSupport.Unsupported, ledger.support)
    }

    @Test
    fun `an ack before the timeout beats a recorded uncorrelated error`() {
        val ledger = ColorSaveLedger<String>()
        val save = ledger.open(1, 5, fur) { "first" }

        assertNull(ledger.uncorrelated("rate_limited", null))
        assertSame(save, ledger.acknowledge(1))

        assertNull(ledger.expire(save, echoExpected = true))
    }

    @Test
    fun `without an ack, a recorded uncorrelated error rejects at the timeout`() {
        val ledger = ColorSaveLedger<String>()
        val first = ledger.open(1, 5, fur) { "first" }
        val second = ledger.open(2, 7, eyes) { "second" }

        assertNull(ledger.uncorrelated("not_owned", 7))

        assertEquals(SaveVerdict.Saved, ledger.expire(first, echoExpected = false))
        assertEquals(SaveVerdict.Rejected("not_owned"), ledger.expire(second, echoExpected = true))
    }

    @Test
    fun `on an old backend echoes and errors settle at once`() {
        val ledger = ColorSaveLedger<String>()
        ledger.expire(ledger.open(1, 5, fur) { "probe" }.also { ledger.echo(5, fur) }, echoExpected = true)
        val first = ledger.open(2, 5, fur) { "first" }
        val second = ledger.open(3, 7, eyes) { "second" }
        val third = ledger.open(4, 9, eyes) { "third" }

        assertEquals(listOf(first), ledger.echo(5, fur))
        assertSame(third, ledger.uncorrelated("rate_limited", 9))
        assertSame(second, ledger.uncorrelated("rate_limited", null))
    }

    @Test
    fun `echoes only match saves with the same target and colours`() {
        val ledger = ColorSaveLedger<String>()
        ledger.expire(ledger.open(1, 5, fur) { "probe" }.also { ledger.echo(5, fur) }, echoExpected = true)
        ledger.open(2, 5, fur) { "cosmetic" }
        val particle = ledger.open(3, null, -48060) { "particle" }

        assertTrue(ledger.echo(5, eyes).isEmpty())
        assertTrue(ledger.echo(null, 1).isEmpty())
        assertEquals(listOf(particle), ledger.echo(null, -48060))
    }

    @Test
    fun `a late ack still proves the backend sends acks`() {
        val ledger = ColorSaveLedger<String>()
        val save = ledger.open(1, null, 7) { "particle" }

        assertEquals(SaveVerdict.Saved, ledger.expire(save, echoExpected = false))
        assertNull(ledger.acknowledge(1))

        assertEquals(AckSupport.Supported, ledger.support)
    }

    @Test
    fun `a new save for the same cosmetic sees the one still pending`() {
        val ledger = ColorSaveLedger<String>()
        val first = ledger.open(1, 5, fur) { earlier -> if (earlier == null) "none" else "after ${earlier.value}" }
        ledger.open(2, 7, eyes) { "other" }
        val second = ledger.open(3, 5, eyes) { earlier -> if (earlier == null) "none" else "after ${earlier.value}" }

        assertEquals("none", first.value)
        assertEquals("after none", second.value)
    }

    @Test
    fun `a settled save finds the later saves for the same cosmetic that are still pending`() {
        val ledger = ColorSaveLedger<String>()
        val first = ledger.open(1, 5, fur) { "first" }
        ledger.open(2, 7, eyes) { "other" }
        val second = ledger.open(3, 5, eyes) { "second" }
        val third = ledger.open(4, 5, fur) { "third" }

        assertSame(first, ledger.acknowledge(1))
        assertEquals(listOf(second, third), ledger.pendingAfter(first))

        assertSame(third, ledger.acknowledge(4))
        assertEquals(listOf(second), ledger.pendingAfter(first))
        assertTrue(ledger.pendingAfter(third).isEmpty())
        assertTrue(ledger.pendingAfter(ledger.open(5, null, 1) { "particle" }).isEmpty())
    }

    @Test
    fun `reset drops every save and forgets what the backend supports`() {
        val ledger = ColorSaveLedger<String>()
        ledger.open(1, 5, fur) { "first" }
        ledger.acknowledge(1)
        val waiting = ledger.open(2, 7, eyes) { "second" }

        assertEquals(listOf(waiting), ledger.reset())

        assertEquals(AckSupport.Unknown, ledger.support)
        assertNull(ledger.expire(waiting, echoExpected = true))
    }
}
