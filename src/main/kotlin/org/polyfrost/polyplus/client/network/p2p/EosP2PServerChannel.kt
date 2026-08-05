package org.polyfrost.polyplus.client.network.p2p

import io.netty.channel.AbstractServerChannel
import io.netty.channel.ChannelMetadata
import io.netty.channel.EventLoop
import java.net.SocketAddress
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.eos.EosNotificationHandle
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge

class EosP2PServerChannel internal constructor() : AbstractServerChannel() {
    private val LOGGER = LogManager.getLogger()
    private val bridge: EosSdkBridge
        get() = requireNotNull(EosP2PChannel.Holder.bridge) { "EosP2PServerChannel used before P2PSessionManager.install() was called" }

    private val config = EosP2PChannelConfig(this)

    @Volatile private var localSocket: EosP2PSocketId? = null
    @Volatile private var open = true
    @Volatile private var active = false
    private var requestHandle: EosNotificationHandle? = null

    companion object {
        private val METADATA = ChannelMetadata(false)
    }

    override fun metadata(): ChannelMetadata = METADATA

    override fun config() = config

    override fun isOpen(): Boolean = open

    override fun isActive(): Boolean = open && active

    override fun localAddress0(): SocketAddress? =
        localSocket?.let { socket -> bridge.localUser?.let { EosP2PAddress(it, socket) } }

    override fun remoteAddress0(): SocketAddress? = null

    override fun isCompatible(loop: EventLoop): Boolean = true

    override fun doBind(localAddress: SocketAddress) {
        require(localAddress is EosP2PAddress) { "EosP2PServerChannel can only bind to an EosP2PAddress, got $localAddress" }

        localSocket = localAddress.socket
        requestHandle = bridge.addConnectionRequestHandler(localAddress.socket) { remote ->
            onConnectionRequest(localAddress.socket, remote)
        }
        active = true
        LOGGER.info("Hosting an EOS P2P session on socket {}", localAddress.socket)
    }

    override fun doClose() {
        open = false
        active = false
        requestHandle?.let(bridge::removeNotificationHandler)
        localSocket?.let { socket -> bridge.closeConnection(socket, null) }
    }

    override fun doBeginRead() {
        // No-op
    }

    private fun onConnectionRequest(socket: EosP2PSocketId, remote: EosProductUserId) {
        bridge.acceptConnection(socket, remote)

        val child = EosP2PChannel(this)
        child.setupAccepted(socket, remote)

        eventLoop().execute {
            pipeline().fireChannelRead(child)
            pipeline().fireChannelReadComplete()
        }
    }
}
