package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emotes.duo.DuoStop

class DuoStopTest {
    @Test
    fun `a pending marker during a duo still stops the duo`() {
        assertEquals(DuoStop.Shared, DuoStop.plan(markerCancelled = true, inSession = true, requestsOpen = false, askCancelled = false))
    }

    @Test
    fun `a pending marker with an open request still stops the request`() {
        assertEquals(DuoStop.Shared, DuoStop.plan(markerCancelled = true, inSession = false, requestsOpen = true, askCancelled = false))
    }

    @Test
    fun `a pending marker alone only cancels the marker`() {
        assertEquals(DuoStop.Quiet, DuoStop.plan(markerCancelled = true, inSession = false, requestsOpen = false, askCancelled = false))
    }

    @Test
    fun `a cancelled ask stops nothing else`() {
        assertEquals(DuoStop.Quiet, DuoStop.plan(markerCancelled = false, inSession = false, requestsOpen = false, askCancelled = true))
    }

    @Test
    fun `with nothing shared the solo emote stops`() {
        assertEquals(DuoStop.Solo, DuoStop.plan(markerCancelled = false, inSession = false, requestsOpen = false, askCancelled = false))
    }

    @Test
    fun `a looping solo emote stops when a marker is cancelled`() {
        assertTrue(DuoStop.stopsSolo(DuoStop.Quiet, soloPlaying = true))
    }

    @Test
    fun `a looping solo emote stops when an open request is stopped`() {
        assertTrue(DuoStop.stopsSolo(DuoStop.Shared, soloPlaying = true))
    }

    @Test
    fun `a shared stop leaves the duo playback to the session stop`() {
        assertFalse(DuoStop.stopsSolo(DuoStop.Shared, soloPlaying = false))
    }

    @Test
    fun `a quiet stop without a solo emote stops nothing`() {
        assertFalse(DuoStop.stopsSolo(DuoStop.Quiet, soloPlaying = false))
    }

    @Test
    fun `the solo plan always stops the controller`() {
        assertTrue(DuoStop.stopsSolo(DuoStop.Solo, soloPlaying = false))
    }
}
