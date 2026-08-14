package org.polyfrost.polyplus.client.network.p2p

import de.maxhenkel.voicechat.api.RawUdpPacket
import de.maxhenkel.voicechat.api.VoicechatSocket
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class EosVoicechatServerSocket(private val bridge: EosSdkBridge) : VoicechatSocket {
    private val logger = LogManager.getLogger()
    private val inbound = LinkedBlockingQueue<RawUdpPacket>()

    @Volatile private var closed = false
    @Volatile private var udpSocket: DatagramSocket? = null

    override fun open(port: Int, bindAddress: String) {
        logger.info(
            "Opening the PolyPlus P2P voice chat server bridge on {}:{}",
            bindAddress,
            port,
        )
        EosVoicechatBridge.registerServerSocket(this)

        val address = if (bindAddress.isBlank() || bindAddress == "*") InetAddress.getByName("0.0.0.0") else InetAddress.getByName(bindAddress)
        val socket = DatagramSocket(port, address)
        udpSocket = socket
        logger.info("Bound a real UDP voice chat socket on {}:{}.", address.hostAddress, socket.localPort)

        Thread({
            val buffer = ByteArray(MAX_UDP_PACKET_BYTES)
            while (!closed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    inbound.offer(EosRawUdpPacket(data, System.currentTimeMillis(), packet.socketAddress))
                } catch (e: IOException) {
                    if (!closed) logger.warn("Reading from the real UDP voice chat socket failed", e)
                }
            }
        }, "polyplus-voicechat-udp-reader").apply {
            isDaemon = true
            start()
        }
    }

    override fun read(): RawUdpPacket {
        while (!closed) {
            return inbound.poll(READ_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: continue
        }
        throw IOException("Voice chat server socket was closed")
    }

    override fun send(data: ByteArray, address: SocketAddress) {
        if (address is EosVoicechatAddress) {
            bridge.sendPacket(EosVoicechatBridge.SOCKET_ID, address.remote, ByteBuffer.wrap(data), EosSdkBridge.PacketReliability.UnreliableUnordered)
            return
        }

        val socket = udpSocket
        if (socket == null || socket.isClosed) {
            logger.warn("Asked to send a UDP voice packet to {}, but our UDP socket isn't open, dropping it.", address)
            return
        }
        runCatching { socket.send(DatagramPacket(data, data.size, address)) }
            .onFailure { logger.warn("Failed to send a UDP voice packet to {}", address, it) }
    }

    override fun getLocalPort(): Int = udpSocket?.localPort ?: 0

    override fun close() {
        closed = true
        udpSocket?.close()
        EosVoicechatBridge.unregisterServerSocket(this)
        logger.info("Closed the PolyPlus P2P voice chat server bridge.")
    }

    override fun isClosed(): Boolean = closed

    fun offer(remote: EosProductUserId, data: ByteArray): Boolean {
        if (closed) return false
        inbound.offer(EosRawUdpPacket(data, System.currentTimeMillis(), EosVoicechatAddress(remote)))
        return true
    }
}

private const val MAX_UDP_PACKET_BYTES = 65536
