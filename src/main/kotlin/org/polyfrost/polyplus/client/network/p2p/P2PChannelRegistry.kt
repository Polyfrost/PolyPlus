package org.polyfrost.polyplus.client.network.p2p

import java.util.concurrent.ConcurrentHashMap
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId

internal object P2PChannelRegistry {
    private data class Key(val socket: EosP2PSocketId, val remote: EosProductUserId)

    private val channels = ConcurrentHashMap<Key, EosP2PChannel>()

    fun register(socket: EosP2PSocketId, remote: EosProductUserId, channel: EosP2PChannel) {
        channels[Key(socket, remote)] = channel
    }

    fun unregister(socket: EosP2PSocketId, remote: EosProductUserId) {
        channels.remove(Key(socket, remote))
    }

    fun get(socket: EosP2PSocketId, remote: EosProductUserId): EosP2PChannel? = channels[Key(socket, remote)]

    fun allChannels(): Collection<EosP2PChannel> = channels.values

    fun connectedPeers(): List<EosProductUserId> = channels.values.mapNotNull { it.currentRemote() }.distinct()
}
