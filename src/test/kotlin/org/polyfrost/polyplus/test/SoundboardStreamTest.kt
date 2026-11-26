package org.polyfrost.polyplus.test

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import io.github.jaredmdobson.concentus.OpusSignal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.http.responses.VoiceTransport
import org.polyfrost.polyplus.client.party.LocalMonitor
import org.polyfrost.polyplus.client.party.PartyAudio
import org.polyfrost.polyplus.client.party.PeerMix
import org.polyfrost.polyplus.client.party.VoicePackets
import org.polyfrost.polyplus.client.party.soundboard.SoundboardStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SoundboardStreamTest {
    private val encoder = OpusEncoder(PartyAudio.SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO).apply {
        bitrate = 48_000
        complexity = 5
        signalType = OpusSignal.OPUS_SIGNAL_MUSIC
    }
    private var seq = 0
    private var sample = 0

    @BeforeEach
    fun setUp() {
        setMonitorOpening(true)
        SoundboardStream.reset()
        SoundboardStream.deafened = false
        SoundboardStream.outputGain = 1f
        SoundboardStream.mix = mapOf("alice" to PeerMix(1f, 0f, false), "bob" to PeerMix(1f, 0f, true))
    }

    @AfterEach
    fun tearDown() {
        SoundboardStream.reset()
        SoundboardStream.mix = emptyMap()
        setMonitorOpening(false)
    }

    private fun setMonitorOpening(value: Boolean) {
        LocalMonitor::class.java.getDeclaredField("opening").apply { isAccessible = true }.setBoolean(null, value)
    }

    private fun packet(last: Boolean = false, version: Byte = VoicePackets.VERSION): ByteArray {
        val pcm = ShortArray(PartyAudio.FRAME_SAMPLES) {
            (sin(2 * PI * 440 * sample++ / PartyAudio.SAMPLE_RATE) * 12_000).toInt().toShort()
        }
        val encoded = ByteArray(PartyAudio.MAX_PACKET)
        val length = encoder.encode(pcm, 0, PartyAudio.FRAME_SAMPLES, encoded, 0, encoded.size)
        val packet = ByteArray(5 + length)
        packet[0] = VoicePackets.SOUNDBOARD
        packet[1] = version
        packet[2] = (seq shr 8).toByte()
        packet[3] = seq.toByte()
        packet[4] = if (last) 1 else 0
        System.arraycopy(encoded, 0, packet, 5, length)
        seq = (seq + 1) and 0xFFFF
        return packet
    }

    private fun burst(count: Int = 3): List<ByteArray> = List(count) { packet(last = it == count - 1) }

    private fun mixFrames(frames: Int): Pair<Boolean, ShortArray> {
        val all = ShortArray(PartyAudio.FRAME_SAMPLES * 2 * frames)
        var busy = false
        repeat(frames) { index ->
            val out = ShortArray(PartyAudio.FRAME_SAMPLES * 2)
            busy = SoundboardStream.mixRemote(out) || busy
            out.copyInto(all, index * out.size)
        }
        return busy to all
    }

    private fun peak(samples: ShortArray, channel: Int): Int {
        var peak = 0
        for (i in channel until samples.size step 2) peak = maxOf(peak, abs(samples[i].toInt()))
        return peak
    }

    @Test
    fun `a pumped packet from a known sender is heard`() {
        burst(4).forEach { SoundboardStream.receive("alice", it) }

        val (busy, out) = mixFrames(4)
        assertTrue(busy)
        assertTrue(peak(out, 0) > 1_000, "left peak ${peak(out, 0)}")
        assertTrue(peak(out, 1) > 1_000, "right peak ${peak(out, 1)}")
    }

    @Test
    fun `packets are routed to the soundboard stream by type`() {
        burst().forEach { VoicePackets.route("alice", it, VoiceTransport.P2p) }

        assertTrue(mixFrames(3).first)
    }

    @Test
    fun `a packet with the wrong version is not routed`() {
        repeat(3) { VoicePackets.route("alice", packet(last = it == 2, version = 2), VoiceTransport.P2p) }

        assertFalse(mixFrames(3).first)
    }

    @Test
    fun `a silenced sender is dropped`() {
        burst().forEach { SoundboardStream.receive("bob", it) }

        assertFalse(mixFrames(3).first)
    }

    @Test
    fun `an unknown sender is dropped`() {
        burst().forEach { SoundboardStream.receive("mallory", it) }

        assertFalse(mixFrames(3).first)
    }

    @Test
    fun `nothing is received while deafened`() {
        SoundboardStream.deafened = true
        burst().forEach { SoundboardStream.receive("alice", it) }
        SoundboardStream.deafened = false

        assertFalse(mixFrames(3).first)
    }

    @Test
    fun `short packets are dropped`() {
        SoundboardStream.receive("alice", byteArrayOf(VoicePackets.SOUNDBOARD, VoicePackets.VERSION, 0, 0))
        SoundboardStream.receive("alice", ByteArray(0))

        assertFalse(mixFrames(3).first)
    }

    @Test
    fun `retain forgets senders that left`() {
        burst().forEach { SoundboardStream.receive("alice", it) }
        SoundboardStream.retain(setOf("bob"))

        assertFalse(mixFrames(3).first)
    }

    @Test
    fun `retain keeps senders still present`() {
        burst().forEach { SoundboardStream.receive("alice", it) }
        SoundboardStream.retain(setOf("alice"))

        assertTrue(mixFrames(3).first)
    }

    @Test
    fun `full left pan keeps the right channel silent`() {
        SoundboardStream.mix = mapOf("alice" to PeerMix(1f, -1f, false))
        burst(4).forEach { SoundboardStream.receive("alice", it) }

        val (busy, out) = mixFrames(4)
        assertTrue(busy)
        assertTrue(peak(out, 0) > 1_000)
        assertEquals(0, peak(out, 1))
    }

    @Test
    fun `a sender silenced after sending is not mixed`() {
        burst().forEach { SoundboardStream.receive("alice", it) }
        SoundboardStream.mix = mapOf("alice" to PeerMix(1f, 0f, true))

        val (_, out) = mixFrames(3)
        assertEquals(0, peak(out, 0))
        assertEquals(0, peak(out, 1))
    }
}
