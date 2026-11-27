package org.polyfrost.polyplus.test

import com.jcraft.jogg.Packet
import com.jcraft.jogg.Page
import com.jcraft.jogg.StreamState
import com.jcraft.jogg.SyncState
import com.jcraft.jorbis.Comment
import com.jcraft.jorbis.Info
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticSoundLimits
import java.io.ByteArrayOutputStream

class CosmeticSoundLimitsTest {
    private val pop = javaClass.getResourceAsStream("/cosmetics/pop.ogg")!!.use { it.readBytes() }

    private class Raw(val bytes: ByteArray, val granule: Long, val eos: Boolean)

    private fun packets(bytes: ByteArray): List<Raw> {
        val sync = SyncState()
        val stream = StreamState()
        val page = Page()
        val packet = Packet()
        sync.init()
        val offset = sync.buffer(bytes.size)
        System.arraycopy(bytes, 0, sync.data, offset, bytes.size)
        sync.wrote(bytes.size)
        val out = mutableListOf<Raw>()
        var started = false
        while (sync.pageout(page) == 1) {
            if (!started) {
                stream.init(page.serialno())
                started = true
            }
            stream.pagein(page)
            while (stream.packetout(packet) == 1) {
                out += Raw(packet.packet_base.copyOfRange(packet.packet, packet.packet + packet.bytes), packet.granulepos, packet.e_o_s != 0)
            }
        }
        return out
    }

    private fun mux(packets: List<ByteArray>, granule: (Int) -> Long): ByteArray {
        val stream = StreamState()
        stream.init(7)
        val page = Page()
        val sink = ByteArrayOutputStream()
        fun drain(flush: Boolean) {
            while (if (flush) stream.flush(page) != 0 else stream.pageout(page) != 0) {
                sink.write(page.header_base, page.header, page.header_len)
                sink.write(page.body_base, page.body, page.body_len)
            }
        }
        packets.forEachIndexed { index, bytes ->
            val packet = Packet()
            packet.packet_base = bytes
            packet.packet = 0
            packet.bytes = bytes.size
            packet.b_o_s = if (index == 0) 1 else 0
            packet.e_o_s = if (index == packets.lastIndex) 1 else 0
            packet.granulepos = granule(index)
            packet.packetno = index.toLong()
            stream.packetin(packet)
            drain(index < 3)
        }
        drain(true)
        return sink.toByteArray()
    }

    private fun repeated(times: Int, granule: (Int) -> Long): ByteArray {
        val all = packets(pop)
        val audio = all.drop(3).map { it.bytes }
        return mux(all.take(3).map { it.bytes } + List(times) { audio }.flatten(), granule)
    }

    @Test
    fun `a short vorbis sound is accepted with its decoded length`() {
        val samples = CosmeticSoundLimits.samples(pop)
        assertTrue(samples > 0)
        assertTrue(samples <= CosmeticSoundLimits.maxSamples(48_000))
    }

    @Test
    fun `a re-muxed copy of the same packets counts the same samples`() {
        val raw = packets(pop)
        val copy = mux(raw.map { it.bytes }) { raw[it].granule }
        assertEquals(CosmeticSoundLimits.samples(pop), CosmeticSoundLimits.samples(copy))
    }

    @Test
    fun `a sound longer than the cap is rejected`() {
        val once = CosmeticSoundLimits.samples(pop)
        val times = (CosmeticSoundLimits.maxSamples(48_000) / once + 2).toInt()
        assertEquals(-1L, CosmeticSoundLimits.samples(repeated(times) { 0L }))
    }

    @Test
    fun `a granule that claims a short length does not hide a long stream`() {
        val once = CosmeticSoundLimits.samples(pop)
        val times = ((CosmeticSoundLimits.maxSamples(48_000) + CosmeticSoundLimits.PADDING_SLACK) / once + 2).toInt()
        assertEquals(-1L, CosmeticSoundLimits.samples(repeated(times) { if (it < 3) 0L else 1L }))
    }

    private fun blockSizes(raw: List<Raw>): List<Int> {
        val info = Info()
        val comment = Comment()
        info.init()
        comment.init()
        return raw.mapIndexed { index, it ->
            val packet = Packet()
            packet.packet_base = it.bytes
            packet.packet = 0
            packet.bytes = it.bytes.size
            packet.b_o_s = if (index == 0) 1 else 0
            if (index < 3) {
                info.synthesis_headerin(comment, packet)
                0
            } else {
                info.blocksize(packet)
            }
        }
    }

    private fun padded(over: Long, finalGranule: (Long) -> Long): Pair<ByteArray, Long> {
        val raw = packets(pop)
        val sizes = blockSizes(raw).drop(3)
        val audio = raw.drop(3).map { it.bytes }
        val chosen = mutableListOf<ByteArray>()
        var previous = 0
        var total = 0L
        var index = 0
        while (total <= over) {
            val size = sizes[index % sizes.size]
            if (previous > 0) total += previous / 4 + size / 4
            previous = size
            chosen += audio[index % audio.size]
            index++
        }
        val identification = raw[0].bytes.copyOf()
        for (i in 0 until 4) identification[12 + i] = (48_000 ushr (8 * i)).toByte()
        val all = listOf(identification) + raw.drop(1).take(2).map { it.bytes } + chosen
        val granule = finalGranule(total)
        return mux(all) { if (it == all.lastIndex) granule else if (it < 3) 0L else -1L } to total
    }

    @Test
    fun `a sound just under the cap is measured by its final granule, not its padded blocks`() {
        val (bytes, total) = padded(CosmeticSoundLimits.maxSamples(48_000)) { 143_952L }
        assertTrue(total > CosmeticSoundLimits.maxSamples(48_000))
        assertEquals(143_952L, CosmeticSoundLimits.samples(bytes))
    }

    @Test
    fun `a final granule past the decodable samples is rejected`() {
        val (bytes, _) = padded(48_000L) { it + 1 }
        assertEquals(-1L, CosmeticSoundLimits.samples(bytes))
    }

    @Test
    fun `a final granule over the cap is rejected`() {
        val (bytes, _) = padded(CosmeticSoundLimits.maxSamples(48_000)) { CosmeticSoundLimits.maxSamples(48_000) + 1 }
        assertEquals(-1L, CosmeticSoundLimits.samples(bytes))
    }

    @Test
    fun `more than two channels is rejected`() {
        val raw = packets(pop).map { it.bytes.copyOf() }
        raw[0][11] = 3
        assertEquals(-1L, CosmeticSoundLimits.samples(mux(raw) { 0L }))
    }

    @Test
    fun `a sample rate above the cap is rejected`() {
        val raw = packets(pop).map { it.bytes.copyOf() }
        val rate = 192_000
        for (i in 0 until 4) raw[0][12 + i] = (rate ushr (8 * i)).toByte()
        assertEquals(-1L, CosmeticSoundLimits.samples(mux(raw) { 0L }))
    }

    @Test
    fun `garbage is rejected`() {
        assertEquals(-1L, CosmeticSoundLimits.samples(ByteArray(64) { it.toByte() }))
    }

    @Test
    fun `decoded buffers never exceed the cap`() {
        assertEquals(48_000 * 3 * 2 * 2, CosmeticSoundLimits.pcmBytes(Long.MAX_VALUE, 48_000, 2))
    }
}
