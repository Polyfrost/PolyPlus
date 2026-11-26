package org.polyfrost.polyplus.client.party

import org.polyfrost.polyplus.client.network.http.responses.VoiceTransport
import org.polyfrost.polyplus.client.party.soundboard.SoundboardStream

internal object VoicePackets {
    const val VERSION: Byte = 1
    const val SOUNDBOARD: Byte = 0x04

    fun route(puid: String, packet: ByteArray, transport: VoiceTransport) {
        if (packet.size < 2 || packet[1] != VERSION) return
        when (packet[0]) {
            SOUNDBOARD -> SoundboardStream.receive(puid, packet)
        }
    }
}
