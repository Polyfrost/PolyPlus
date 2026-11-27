package org.polyfrost.polyplus.client.party.soundboard

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import io.github.jaredmdobson.concentus.OpusSignal
import org.polyfrost.polyplus.client.network.http.responses.VoiceTransport
import org.polyfrost.polyplus.client.party.JitterBuffer
import org.polyfrost.polyplus.client.party.LocalMonitor
import org.polyfrost.polyplus.client.party.PartyAudio
import org.polyfrost.polyplus.client.party.PartyMesh
import org.polyfrost.polyplus.client.party.PeerMix
import org.polyfrost.polyplus.client.party.RtcVoice
import org.polyfrost.polyplus.client.party.VoicePackets
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

internal object SoundboardStream {
    private const val BITRATE = 48_000
    private const val HEADER = 5
    private const val SQRT2 = 1.4142135f

    private class Remote {
        val jitter = JitterBuffer()
    }

    private val remotes = ConcurrentHashMap<String, Remote>()
    private val frame = ShortArray(PartyAudio.FRAME_SAMPLES)
    private val encoded = ByteArray(PartyAudio.MAX_PACKET)
    private var encoder: OpusEncoder? = null
    private var seq = 0
    private var sending = false

    @Volatile var mix: Map<String, PeerMix> = emptyMap()
    @Volatile var deafened = false
    @Volatile var outputGain = 1f

    @Synchronized
    fun pump(transport: VoiceTransport?, allowed: Boolean) {
        frame.fill(0)
        val active = Soundboard.mixOutgoing(frame, allowed && transport != null)
        if (!active && !sending) return
        val last = !active
        sending = active
        if (last) frame.fill(0)
        val opus = encoder ?: newEncoder().also { encoder = it }
        val length = runCatching { opus.encode(frame, 0, PartyAudio.FRAME_SAMPLES, encoded, 0, encoded.size) }.getOrDefault(0)
        if (length <= 0) return
        val packet = ByteArray(HEADER + length)
        packet[0] = VoicePackets.SOUNDBOARD
        packet[1] = VoicePackets.VERSION
        packet[2] = (seq shr 8).toByte()
        packet[3] = seq.toByte()
        packet[4] = if (last) 1 else 0
        System.arraycopy(encoded, 0, packet, HEADER, length)
        seq = (seq + 1) and 0xFFFF
        when (transport) {
            VoiceTransport.P2p -> PartyMesh.broadcast(packet)
            VoiceTransport.Rtc -> RtcVoice.sendData(packet)
            else -> Unit
        }
    }

    @Synchronized
    fun reset() {
        sending = false
        encoder = null
        remotes.clear()
    }

    fun receive(puid: String, packet: ByteArray) {
        if (packet.size < HEADER || deafened) return
        val setting = mix[puid] ?: return
        if (setting.silenced) return
        val sequence = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        val last = packet[4].toInt() and 1 == 1
        val opus = packet.copyOfRange(HEADER, packet.size)
        remotes.getOrPut(puid) { Remote() }.jitter.push(sequence, last, opus)
        LocalMonitor.wake()
    }

    fun retain(puids: Set<String>) {
        remotes.keys.retainAll(puids)
    }

    fun mixRemote(out: ShortArray): Boolean {
        if (remotes.isEmpty()) return false
        var busy = false
        for ((puid, remote) in remotes) {
            val pcm = remote.jitter.next() ?: continue
            busy = true
            val setting = mix[puid] ?: continue
            if (setting.silenced || deafened) continue
            val gain = setting.gain * outputGain
            if (gain <= 0f) continue
            val pan = setting.pan.coerceIn(-1f, 1f)
            val left = gain * sqrt((1f - pan) / 2f) * SQRT2
            val right = gain * sqrt((1f + pan) / 2f) * SQRT2
            for (i in pcm.indices) {
                val sample = pcm[i].toFloat()
                out[i * 2] = clip(out[i * 2] + sample * left)
                out[i * 2 + 1] = clip(out[i * 2 + 1] + sample * right)
            }
        }
        return busy
    }

    private fun clip(value: Float): Short = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private fun newEncoder(): OpusEncoder = OpusEncoder(PartyAudio.SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO).apply {
        bitrate = BITRATE
        complexity = 5
        signalType = OpusSignal.OPUS_SIGNAL_MUSIC
    }
}
