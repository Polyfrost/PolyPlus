package org.polyfrost.polyplus.client.party

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import org.polyfrost.polyplus.client.network.http.PartyApi
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PeerMix(val gain: Float, val pan: Float, val silenced: Boolean)

internal object PartyMesh {
    private val LOGGER = LogManager.getLogger()

    val SOCKET = EosP2PSocketId("polyplus-party-voice")

    private const val VERSION: Byte = 1
    private const val HELLO: Byte = 0x01
    private const val AUDIO: Byte = 0x02
    private const val STATE: Byte = 0x03
    private const val SPEAKING_MS = 300L
    private const val PASS_REFRESH_EARLY_MS = 60_000L
    private const val PASS_RETRY_MS = 15_000L
    private const val HELLO_RETRY_MS = 5_000L

    private class Peer(val puid: EosProductUserId, val initiator: Boolean) {
        @Volatile var accepted = false
        @Volatile var verifying = false
        @Volatile var helloSentAt = 0L
        @Volatile var remoteMuted = false
        @Volatile var remoteDeafened = false
        val jitter = JitterBuffer()
    }

    private val peers = ConcurrentHashMap<String, Peer>()

    @Volatile private var bridge: EosSdkBridge? = null
    @Volatile private var partyId: String? = null
    @Volatile private var pass: String? = null
    @Volatile private var wanted: Set<String> = emptySet()
    @Volatile private var passJob: Job? = null
    @Volatile private var speaker: VoiceStream? = null
    @Volatile private var encoder = PartyAudio.encoder()
    @Volatile private var seq = 0
    @Volatile private var sending = false

    @Volatile var mix: Map<String, PeerMix> = emptyMap()
    @Volatile var deafened = false
    @Volatile var outputGain = 1f

    val running: Boolean get() = partyId != null

    fun start(party: String) {
        if (partyId == party) return
        stop()
        partyId = party
        encoder = PartyAudio.encoder()
        seq = 0
        speaker = PartyAudio.openSpeaker(PartyVoiceSettings.outputDevice(), ::mixInto)
        if (speaker == null) LOGGER.warn("Party voice has no speaker; you won't hear your party")
        passJob = PolyPlusClient.SCOPE.launch { keepPass(party) }
    }

    fun stop() {
        val party = partyId ?: return
        partyId = null
        passJob?.cancel()
        passJob = null
        pass = null
        speaker?.stop()
        speaker = null
        val current = bridge
        peers.values.forEach { peer -> current?.closeConnection(SOCKET, peer.puid) }
        peers.clear()
        wanted = emptySet()
        LOGGER.info("Stopped peer-to-peer party voice for party {}", party)
    }

    private suspend fun keepPass(party: String) {
        while (kotlin.coroutines.coroutineContext.isActive && partyId == party) {
            val result = PartyApi.voicePass()
            val fresh = result.getOrNull()
            if (fresh == null) {
                LOGGER.warn("Could not get a party voice pass", result.exceptionOrNull())
                delay(PASS_RETRY_MS)
                continue
            }
            pass = fresh.pass
            peers.values.forEach { sendHello(it, force = true) }
            val expires = VoicePassVerifier.expiresAt(fresh.pass)
                ?: runCatching { Instant.parse(fresh.expiresAt).toEpochMilli() }.getOrNull()
                ?: (System.currentTimeMillis() + PASS_RETRY_MS * 4)
            delay(max(PASS_RETRY_MS, expires - System.currentTimeMillis() - PASS_REFRESH_EARLY_MS))
        }
    }

    fun sync(members: Map<String, Boolean>) {
        if (partyId == null) return
        val current = P2PSessionManager.activeBridge
        if (current !== bridge) {
            peers.clear()
            bridge = current
            current?.addConnectionRequestHandler(SOCKET, ::onConnectionRequest)
        }
        val bridge = current ?: return
        val local = bridge.localUser?.raw ?: return
        wanted = members.keys
        peers.keys.filterNot { it in members }.forEach { puid ->
            peers.remove(puid)?.let { bridge.closeConnection(SOCKET, it.puid) }
        }
        val now = System.currentTimeMillis()
        members.forEach { (puid, active) ->
            if (!active || puid == local) return@forEach
            if (local < puid) {
                val peer = peers.getOrPut(puid) { Peer(EosProductUserId(puid), initiator = true) }
                if (peer.helloSentAt == 0L || (!peer.accepted && now - peer.helloSentAt > HELLO_RETRY_MS)) {
                    if (peer.helloSentAt == 0L) bridge.acceptConnection(SOCKET, peer.puid)
                    sendHello(peer, force = true)
                }
            }
        }
    }

    private fun onConnectionRequest(remote: EosProductUserId) {
        val bridge = bridge ?: return
        val local = bridge.localUser?.raw ?: return
        if (partyId == null || remote.raw !in wanted || remote.raw >= local) {
            LOGGER.info("Ignoring a party voice connection from {}", remote)
            return
        }
        val peer = Peer(remote, initiator = false)
        peers[remote.raw] = peer
        bridge.acceptConnection(SOCKET, remote)
        sendHello(peer, force = true)
    }

    private fun sendHello(peer: Peer, force: Boolean) {
        val bridge = bridge ?: return
        val token = pass ?: return
        if (!force && peer.helloSentAt != 0L) return
        peer.helloSentAt = System.currentTimeMillis()
        val body = token.toByteArray(Charsets.UTF_8)
        bridge.sendPacket(SOCKET, peer.puid, frame(HELLO, body), EosSdkBridge.PacketReliability.ReliableOrdered)
    }

    private fun frame(type: Byte, body: ByteArray, prefix: Int = 0, prefixBytes: Int = 0): ByteBuffer {
        val buffer = ByteBuffer.allocate(2 + prefixBytes + body.size)
        buffer.put(type).put(VERSION)
        when (prefixBytes) {
            3 -> buffer.putShort((prefix shr 8).toShort()).put(prefix.toByte())
            1 -> buffer.put(prefix.toByte())
        }
        buffer.put(body)
        buffer.flip()
        return buffer
    }

    fun handlePacket(received: EosSdkBridge.Received): Boolean {
        if (received.socket != SOCKET) return false
        val party = partyId ?: return true
        val data = received.data
        if (data.remaining() < 2) return true
        val type = data.get()
        if (data.get() != VERSION) return true
        val peer = peers[received.remote.raw] ?: return true
        when (type) {
            HELLO -> {
                val bytes = ByteArray(data.remaining()).also(data::get)
                val token = String(bytes, Charsets.UTF_8)
                if (peer.verifying) return true
                peer.verifying = true
                PolyPlusClient.SCOPE.launch {
                    val ok = VoicePassVerifier.verify(token, party, received.remote.raw)
                    peer.verifying = false
                    if (!ok) {
                        LOGGER.warn("Rejected a party voice pass from {}", received.remote)
                        return@launch
                    }
                    val first = !peer.accepted
                    peer.accepted = true
                    if (first && !peer.initiator) sendHello(peer, force = false)
                    if (first) sendStateTo(peer)
                }
            }
            AUDIO -> {
                if (!peer.accepted || data.remaining() < 3) return true
                val high = data.get().toInt() and 0xFF
                val low = data.get().toInt() and 0xFF
                val flags = data.get().toInt()
                val opus = ByteArray(data.remaining()).also(data::get)
                if (speaker == null || deafened || mix[received.remote.raw]?.silenced == true) return true
                peer.jitter.push((high shl 8) or low, flags and 1 == 1, opus)
            }
            STATE -> {
                if (!peer.accepted || data.remaining() < 1) return true
                val bits = data.get().toInt()
                peer.remoteMuted = bits and 1 != 0
                peer.remoteDeafened = bits and 2 != 0
            }
        }
        return true
    }

    private var lastState = 0

    fun sendState(muted: Boolean, deafened: Boolean) {
        val bits = (if (muted) 1 else 0) or (if (deafened) 2 else 0)
        if (bits == lastState) return
        lastState = bits
        peers.values.filter { it.accepted }.forEach(::sendStateTo)
    }

    private fun sendStateTo(peer: Peer) {
        bridge?.sendPacket(SOCKET, peer.puid, frame(STATE, ByteArray(0), lastState, 1), EosSdkBridge.PacketReliability.ReliableOrdered)
    }

    private val encoded = ByteArray(PartyAudio.MAX_PACKET)

    fun onCapture(frame: ShortArray, transmit: Boolean) {
        if (partyId == null) return
        val bridge = bridge ?: return
        if (!transmit && !sending) return
        val last = !transmit
        sending = transmit
        val length = runCatching { encoder.encode(frame, 0, PartyAudio.FRAME_SAMPLES, encoded, 0, encoded.size) }.getOrDefault(0)
        if (length <= 0) return
        val body = encoded.copyOf(length)
        val sequence = seq
        seq = (seq + 1) and 0xFFFF
        val packetPrefix = (sequence shl 8) or (if (last) 1 else 0)
        for (peer in peers.values) {
            if (!peer.accepted || mix[peer.puid.raw]?.silenced == true) continue
            bridge.sendPacket(SOCKET, peer.puid, frame(AUDIO, body, packetPrefix, 3), EosSdkBridge.PacketReliability.UnreliableUnordered)
        }
    }

    fun hasListeners(): Boolean = peers.values.any { it.accepted && mix[it.puid.raw]?.silenced != true }

    fun speaking(): Set<String> {
        val now = System.currentTimeMillis()
        return peers.values.filter { now - it.jitter.lastAudioAt < SPEAKING_MS }.mapTo(HashSet()) { it.puid.raw }
    }

    fun connected(): Set<String> = peers.values.filter { it.accepted }.mapTo(HashSet()) { it.puid.raw }

    private fun mixInto(out: ShortArray) {
        if (deafened) {
            peers.values.forEach { it.jitter.clear() }
            return
        }
        val master = outputGain
        for (peer in peers.values) {
            val pcm = peer.jitter.next() ?: continue
            val setting = mix[peer.puid.raw] ?: PeerMix(1f, 0f, false)
            if (setting.silenced) continue
            val gain = setting.gain * master
            if (gain <= 0f) continue
            val pan = setting.pan.coerceIn(-1f, 1f)
            val left = gain * sqrt((1f - pan) / 2f) * SQRT2
            val right = gain * sqrt((1f + pan) / 2f) * SQRT2
            for (i in pcm.indices) {
                val sample = pcm[i].toFloat()
                out[i * 2] = clamp(out[i * 2] + sample * left)
                out[i * 2 + 1] = clamp(out[i * 2 + 1] + sample * right)
            }
        }
    }

    private const val SQRT2 = 1.4142135f

    private fun clamp(value: Float): Short = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}
