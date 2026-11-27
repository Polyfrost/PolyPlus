package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.party.JitterBuffer
import org.polyfrost.polyplus.client.party.PartyAudio

class JitterBufferTest {
    private fun packet(seq: Int): ByteArray {
        val encoder = PartyAudio.encoder()
        val pcm = ShortArray(PartyAudio.FRAME_SAMPLES) { ((it * 37 + seq) % 2000 - 1000).toShort() }
        val out = ByteArray(PartyAudio.MAX_PACKET)
        val length = encoder.encode(pcm, 0, PartyAudio.FRAME_SAMPLES, out, 0, out.size)
        return out.copyOf(length)
    }

    @Test
    fun `plays packets in sequence order after the start depth`() {
        val buffer = JitterBuffer()
        val data = packet(1)
        buffer.push(7, false, data)
        buffer.push(5, false, data)
        assertNull(buffer.next())
        buffer.push(6, false, data)
        assertNotNull(buffer.next())
        assertNotNull(buffer.next())
        assertNotNull(buffer.next())
    }

    @Test
    fun `plays across sequence wraparound`() {
        val buffer = JitterBuffer()
        val data = packet(2)
        buffer.push(0xFFFF, false, data)
        buffer.push(0, false, data)
        buffer.push(1, false, data)
        repeat(3) { assertNotNull(buffer.next()) }
    }

    @Test
    fun `a last packet ends the stream`() {
        val buffer = JitterBuffer()
        val data = packet(3)
        buffer.push(10, false, data)
        buffer.push(11, true, data)
        assertNotNull(buffer.next())
        assertNotNull(buffer.next())
        assertNull(buffer.next())
    }
}
