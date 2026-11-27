package org.polyfrost.polyplus.client.party

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.polyfrost.polyplus.client.network.http.responses.VoiceTransport
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.party.soundboard.SoundboardStream
import java.util.concurrent.ConcurrentHashMap

internal object VoicePackets {
    const val VERSION: Byte = 1
    const val SOUNDBOARD: Byte = 0x04
    const val PING: Byte = 0x05
    const val PONG: Byte = 0x06

    fun route(puid: String, packet: ByteArray, transport: VoiceTransport) {
        if (packet.size < 2 || packet[1] != VERSION) return
        when (packet[0]) {
            SOUNDBOARD -> SoundboardStream.receive(puid, packet)
            PING -> VoiceLinks.onPing(puid, packet, transport)
            PONG -> VoiceLinks.onPong(puid, packet)
        }
    }
}

enum class LinkQuality { Good, Fair, Poor }

data class VoiceLink(val rttMillis: Int, val lossPercent: Int, val quality: LinkQuality)

internal object VoiceLinks {
    private const val INTERVAL_MS = 2_000L
    private const val TIMEOUT_MS = 3_000L
    private const val WINDOW = 10
    private const val GOOD_RTT = 150
    private const val FAIR_RTT = 300
    private const val GOOD_LOSS = 5
    private const val FAIR_LOSS = 15

    private class Stats {
        val outcomes = ArrayDeque<Boolean>()
        val waiting = HashMap<Int, Long>()
        var rtt = -1f
    }

    private val stats = ConcurrentHashMap<String, Stats>()
    private val _links = MutableStateFlow<Map<String, VoiceLink>>(emptyMap())
    val links = _links.asStateFlow()

    @Volatile internal var clock: () -> Long = System::currentTimeMillis

    @Volatile private var nextPing = 0L
    private var id = 0

    fun reset() {
        stats.clear()
        nextPing = 0L
        if (_links.value.isNotEmpty()) _links.value = emptyMap()
    }

    fun tick(transport: VoiceTransport?, peers: Set<String>) {
        if (transport == null) {
            reset()
            return
        }
        val dropped = stats.keys.retainAll(peers)
        val now = clock()
        if (now < nextPing) {
            if (dropped) publish()
            return
        }
        nextPing = now + INTERVAL_MS
        for (peer in peers) {
            val entry = stats.getOrPut(peer) { Stats() }
            synchronized(entry) {
                val expired = entry.waiting.filterValues { now - it > TIMEOUT_MS }.keys
                expired.forEach {
                    entry.waiting.remove(it)
                    record(entry, false)
                }
                entry.waiting[id] = now
            }
        }
        val packet = byteArrayOf(VoicePackets.PING, VoicePackets.VERSION, (id shr 8).toByte(), id.toByte())
        id = (id + 1) and 0xFFFF
        send(transport, packet, null)
        publish()
    }

    fun onPing(puid: String, packet: ByteArray, transport: VoiceTransport) {
        if (packet.size < 4) return
        val target = puid.toByteArray(Charsets.UTF_8)
        val pong = ByteArray(4 + target.size)
        pong[0] = VoicePackets.PONG
        pong[1] = VoicePackets.VERSION
        pong[2] = packet[2]
        pong[3] = packet[3]
        System.arraycopy(target, 0, pong, 4, target.size)
        send(transport, pong, puid)
    }

    fun onPong(puid: String, packet: ByteArray) {
        if (packet.size < 4) return
        if (packet.size > 4) {
            val target = String(packet, 4, packet.size - 4, Charsets.UTF_8)
            if (target != P2PSessionManager.activeBridge?.localUser?.raw) return
        }
        val entry = stats[puid] ?: return
        val pingId = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        synchronized(entry) {
            val sentAt = entry.waiting.remove(pingId) ?: return
            val rtt = (clock() - sentAt).toFloat()
            entry.rtt = if (entry.rtt < 0f) rtt else entry.rtt * 0.7f + rtt * 0.3f
            record(entry, true)
        }
        publish()
    }

    private fun record(entry: Stats, answered: Boolean) {
        entry.outcomes.addLast(answered)
        while (entry.outcomes.size > WINDOW) entry.outcomes.removeFirst()
    }

    private fun send(transport: VoiceTransport, packet: ByteArray, to: String?) {
        when (transport) {
            VoiceTransport.P2p -> if (to == null) PartyMesh.broadcast(packet) else PartyMesh.sendTo(to, packet)
            VoiceTransport.Rtc -> RtcVoice.sendData(packet)
            else -> Unit
        }
    }

    private fun publish() {
        val next = HashMap<String, VoiceLink>()
        for ((puid, entry) in stats) {
            synchronized(entry) {
                if (entry.rtt < 0f || entry.outcomes.isEmpty()) return@synchronized
                val loss = entry.outcomes.count { !it } * 100 / entry.outcomes.size
                val rtt = entry.rtt.toInt()
                val quality = when {
                    rtt <= GOOD_RTT && loss <= GOOD_LOSS -> LinkQuality.Good
                    rtt <= FAIR_RTT && loss <= FAIR_LOSS -> LinkQuality.Fair
                    else -> LinkQuality.Poor
                }
                next[puid] = VoiceLink(rtt, loss, quality)
            }
        }
        if (next != _links.value) _links.value = next
    }
}
