package org.polyfrost.polyplus.client.network.p2p

import de.maxhenkel.voicechat.api.RawUdpPacket
import java.net.SocketAddress

internal class EosRawUdpPacket(
    private val data: ByteArray,
    private val timestamp: Long,
    private val address: SocketAddress,
) : RawUdpPacket {
    override fun getData(): ByteArray = data
    override fun getTimestamp(): Long = timestamp
    override fun getSocketAddress(): SocketAddress = address
}