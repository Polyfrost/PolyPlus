package org.polyfrost.polyplus.test

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.http.responses.VoiceTransport
import org.polyfrost.polyplus.client.party.LinkQuality
import org.polyfrost.polyplus.client.party.VoiceLink
import org.polyfrost.polyplus.client.party.VoiceLinks
import org.polyfrost.polyplus.client.party.VoicePackets
import kotlin.math.abs

class VoiceLinksTest {
    private var now = 1_000_000L

    @BeforeEach
    fun setUp() {
        VoiceLinks.reset()
        VoiceLinks.clock = { now }
    }

    @AfterEach
    fun tearDown() {
        VoiceLinks.reset()
        VoiceLinks.clock = System::currentTimeMillis
    }

    private fun nextId(): Int = VoiceLinks::class.java.getDeclaredField("id").apply { isAccessible = true }.getInt(null)

    private fun ping(vararg peers: String): Int {
        val id = nextId()
        VoiceLinks.tick(VoiceTransport.Unknown, peers.toSet())
        return id
    }

    private fun pongPacket(id: Int, target: String? = null, version: Byte = VoicePackets.VERSION): ByteArray {
        val suffix = target?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        return byteArrayOf(VoicePackets.PONG, version, (id shr 8).toByte(), id.toByte()) + suffix
    }

    private fun pong(from: String, id: Int) = VoicePackets.route(from, pongPacket(id), VoiceTransport.Unknown)

    private fun link(puid: String): VoiceLink? = VoiceLinks.links.value[puid]

    private fun round(answer: Boolean, rtt: Long = 20, peers: Set<String> = setOf("alice")) {
        val start = now
        val id = ping(*peers.toTypedArray())
        if (answer) {
            now = start + rtt
            peers.forEach { pong(it, id) }
        }
        now = start + 2_000
    }

    @Test
    fun `a quick answer is a good link`() {
        val id = ping("alice")
        now += 40
        pong("alice", id)

        assertEquals(VoiceLink(40, 0, LinkQuality.Good), link("alice"))
    }

    @Test
    fun `round trip time picks the quality bucket`() {
        val cases = linkedMapOf(
            "good-edge" to 150L,
            "fair-low" to 151L,
            "fair-edge" to 300L,
            "poor" to 301L,
        )
        val sent = now
        val id = ping(*cases.keys.toTypedArray())
        for ((peer, rtt) in cases) {
            now = sent + rtt
            pong(peer, id)
        }

        assertEquals(LinkQuality.Good, link("good-edge")?.quality)
        assertEquals(LinkQuality.Fair, link("fair-low")?.quality)
        assertEquals(LinkQuality.Fair, link("fair-edge")?.quality)
        assertEquals(LinkQuality.Poor, link("poor")?.quality)
    }

    @Test
    fun `one timeout in ten pings is ten percent loss and only fair`() {
        round(answer = false)
        repeat(9) { round(answer = true) }
        ping("alice")

        val link = link("alice")
        assertEquals(10, link?.lossPercent)
        assertEquals(LinkQuality.Fair, link?.quality)
    }

    @Test
    fun `old timeouts slide out of the ten ping window`() {
        round(answer = false)
        round(answer = false)
        repeat(12) { round(answer = true) }

        val link = link("alice")
        assertEquals(0, link?.lossPercent)
        assertEquals(LinkQuality.Good, link?.quality)
    }

    @Test
    fun `a peer that stops answering reaches full loss and goes poor`() {
        round(answer = true)
        repeat(14) { round(answer = false) }

        val link = link("alice")
        assertEquals(100, link?.lossPercent)
        assertEquals(LinkQuality.Poor, link?.quality)
    }

    @Test
    fun `a pong that arrives after its timeout is not counted`() {
        val late = ping("alice")
        now += 2_000
        round(answer = true)
        round(answer = false)
        pong("alice", late)

        assertEquals(50, link("alice")?.lossPercent)
    }

    @Test
    fun `a repeated pong is counted once`() {
        round(answer = false)
        now += 2_000
        val id = ping("alice")
        now += 20
        pong("alice", id)
        pong("alice", id)

        assertEquals(50, link("alice")?.lossPercent)
    }

    @Test
    fun `round trip time is smoothed`() {
        round(answer = true, rtt = 100)
        assertEquals(100, link("alice")?.rttMillis)

        round(answer = true, rtt = 200)
        val rtt = link("alice")?.rttMillis ?: -1
        assertTrue(abs(rtt - 130) <= 1, "expected about 130 ms, got $rtt")
    }

    @Test
    fun `a pong addressed to someone else is ignored`() {
        val id = ping("alice")
        now += 20
        VoicePackets.route("alice", pongPacket(id, target = "someone-else"), VoiceTransport.Unknown)

        assertNull(link("alice"))
    }

    @Test
    fun `a pong from someone we never pinged is ignored`() {
        val id = ping("alice")
        now += 20
        pong("mallory", id)

        assertNull(link("mallory"))
        assertTrue(VoiceLinks.links.value.isEmpty())
    }

    @Test
    fun `a peer that never answers has no link`() {
        repeat(6) {
            val start = now
            val id = ping("alice", "bob")
            now = start + 20
            pong("alice", id)
            now = start + 2_000
        }

        assertTrue(link("alice") != null)
        assertNull(link("bob"))
    }

    @Test
    fun `reset clears links and pending pings`() {
        val id = ping("alice")
        now += 20
        pong("alice", id)
        assertTrue(link("alice") != null)

        val pending = ping("alice")
        VoiceLinks.reset()
        assertTrue(VoiceLinks.links.value.isEmpty())

        now += 20
        pong("alice", pending)
        assertTrue(VoiceLinks.links.value.isEmpty())
    }

    @Test
    fun `losing the transport resets links`() {
        round(answer = true)
        assertTrue(link("alice") != null)

        VoiceLinks.tick(null, setOf("alice"))
        assertTrue(VoiceLinks.links.value.isEmpty())
    }

    @Test
    fun `a peer that leaves drops its link at the next ping`() {
        round(answer = true, peers = setOf("alice", "bob"))
        assertTrue(link("bob") != null)

        ping("alice")
        assertNull(link("bob"))
        assertTrue(link("alice") != null)
    }

    @Test
    fun `packets with the wrong version or too short are dropped`() {
        val id = ping("alice")
        now += 20
        VoicePackets.route("alice", pongPacket(id, version = 2), VoiceTransport.Unknown)
        VoicePackets.route("alice", pongPacket(id).copyOf(3), VoiceTransport.Unknown)
        VoicePackets.route("alice", byteArrayOf(VoicePackets.PONG), VoiceTransport.Unknown)
        VoicePackets.route("alice", ByteArray(0), VoiceTransport.Unknown)
        assertNull(link("alice"))

        pong("alice", id)
        assertEquals(20, link("alice")?.rttMillis)
    }

    @Test
    fun `a ping is answered without creating a link`() {
        VoicePackets.route("alice", byteArrayOf(VoicePackets.PING, VoicePackets.VERSION, 0, 7), VoiceTransport.Unknown)
        VoicePackets.route("alice", byteArrayOf(VoicePackets.PING, VoicePackets.VERSION, 0), VoiceTransport.P2p)

        assertTrue(VoiceLinks.links.value.isEmpty())
    }
}
