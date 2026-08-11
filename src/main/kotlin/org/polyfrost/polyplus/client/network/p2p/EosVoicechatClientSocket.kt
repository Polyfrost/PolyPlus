package org.polyfrost.polyplus.client.network.p2p

import de.maxhenkel.voicechat.api.ClientVoicechatSocket
import de.maxhenkel.voicechat.api.RawUdpPacket
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import java.io.IOException
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class EosVoicechatClientSocket(
    private val bridge: EosSdkBridge,
    private val host: EosProductUserId,
) : ClientVoicechatSocket {
    private val logger = LogManager.getLogger()
    private val inbound = LinkedBlockingQueue<ByteArray>()

    @Volatile private var closed = false

    override fun open() {
        logger.info("Opening the PolyPlus P2P voice chat bridge to the host ({})...", host)
        EosVoicechatBridge.registerClientSocket(this)
        bridge.acceptConnection(EosVoicechatBridge.SOCKET_ID, host)
    }

    override fun read(): RawUdpPacket {
        while (!closed) {
            val data = inbound.poll(READ_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: continue
            return EosRawUdpPacket(data, System.currentTimeMillis(), EosVoicechatAddress(host))
        }
        throw IOException("Voice chat socket to $host was closed")
    }

    override fun send(data: ByteArray, address: SocketAddress) {
        bridge.sendPacket(EosVoicechatBridge.SOCKET_ID, host, ByteBuffer.wrap(data), EosSdkBridge.PacketReliability.UnreliableUnordered)
    }

    override fun close() {
        closed = true
        EosVoicechatBridge.unregisterClientSocket(this)
        logger.info("Closed the PolyPlus P2P voice chat bridge to {}.", host)
    }

    override fun isClosed(): Boolean = closed

    fun offer(data: ByteArray): Boolean {
        if (closed) return false
        inbound.offer(data)
        return true
    }
}
