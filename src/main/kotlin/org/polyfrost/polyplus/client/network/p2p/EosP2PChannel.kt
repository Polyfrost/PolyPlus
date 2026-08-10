package org.polyfrost.polyplus.client.network.p2p

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.AbstractChannel
import io.netty.channel.Channel
import io.netty.channel.ChannelConfig
import io.netty.channel.ChannelMetadata
import io.netty.channel.ChannelOutboundBuffer
import io.netty.channel.ChannelPromise
import io.netty.channel.EventLoop
import java.net.SocketAddress
import java.nio.ByteBuffer
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.eos.EosNotificationHandle
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge

class EosP2PChannel internal constructor(parent: Channel?) : AbstractChannel(parent) {
    constructor() : this(null)

    private val LOGGER = LogManager.getLogger()
    private val bridge: EosSdkBridge
        get() = requireNotNull(Holder.bridge) { "EosP2PChannel used before P2PSessionManager.install() was called" }

    internal object Holder {
        @Volatile var bridge: EosSdkBridge? = null
    }

    private val config = EosP2PChannelConfig(this)

    @Volatile private var localSocket: EosP2PSocketId? = null
    @Volatile private var remoteUser: EosProductUserId? = null
    @Volatile private var open = true
    @Volatile private var active = false
    @Volatile private var accepted = false

    private var connectionStateHandle: EosNotificationHandle? = null

    companion object {
        private val METADATA = ChannelMetadata(false)

        // EOS' MTU is ~1170 bytes/packet
        const val MAX_PAYLOAD_BYTES = 1169
    }

    internal fun setupAccepted(socket: EosP2PSocketId, remote: EosProductUserId) {
        localSocket = socket
        remoteUser = remote
        accepted = true
        activate()
    }

    override fun metadata(): ChannelMetadata = METADATA

    override fun config(): ChannelConfig = config

    override fun isOpen(): Boolean = open

    override fun isActive(): Boolean = open && active

    override fun isCompatible(loop: EventLoop): Boolean = true

    override fun localAddress0(): SocketAddress? =
        localSocket?.let { socket -> bridge.localUser?.let { EosP2PAddress(it, socket) } }

    override fun remoteAddress0(): SocketAddress? =
        remoteUser?.let { remote -> localSocket?.let { EosP2PAddress(remote, it) } }

    override fun newUnsafe(): AbstractUnsafe = object : AbstractUnsafe() {
        override fun connect(remoteAddress: SocketAddress, localAddress: SocketAddress?, promise: ChannelPromise) {
            try {
                require(remoteAddress is EosP2PAddress) {
                    "EosP2PChannel can only connect to an EosP2PAddress, got $remoteAddress"
                }
                localSocket = (localAddress as? EosP2PAddress)?.socket ?: remoteAddress.socket
                remoteUser = remoteAddress.user
                activate()
                // Explicitly request the connection now
                bridge.acceptConnection(remoteAddress.socket, remoteAddress.user)
                promise.setSuccess()
                pipeline().fireChannelActive()
            } catch (e: Throwable) {
                promise.setFailure(e)
                close(voidPromise())
            }
        }
    }

    override fun doBind(localAddress: SocketAddress) {
        if (localAddress is EosP2PAddress) {
            localSocket = localAddress.socket
        }
    }

    private fun activate() {
        val socket = requireNotNull(localSocket) { "EosP2PChannel socket must be assigned before activation" }
        val remote = requireNotNull(remoteUser) { "EosP2PChannel remote must be assigned before activation" }
        P2PChannelRegistry.register(socket, remote, this)

        connectionStateHandle = bridge.addConnectionStateHandler(socket) { event ->
            if (event.remote != remoteUser) return@addConnectionStateHandler
            when (event) {
                is EosSdkBridge.ConnectionStateEvent.Established -> {
                    LOGGER.info(
                        "EOS P2P connection established on socket {} ({})",
                        socket,
                        if (event.direct) "direct" else "relayed",
                    )
                    if (accepted && P2PSessionManager.autoShareResourcePack) {
                        ResourcePackShare.shareEquippedPackWith(remote)
                    }
                }
                is EosSdkBridge.ConnectionStateEvent.Interrupted -> {
                    LOGGER.warn("EOS P2P connection on socket {} interrupted, attempting to recover", socket)
                }
                is EosSdkBridge.ConnectionStateEvent.Closed -> {
                    LOGGER.info("EOS P2P connection on socket {} closed: {}", socket, event.reason)
                    eventLoop().execute { unsafe().close(unsafe().voidPromise()) }
                }
            }
        }

        active = true
    }

    override fun doDisconnect() = doClose()

    override fun doClose() {
        open = false
        active = false

        val socket = localSocket
        val remote = remoteUser
        connectionStateHandle?.let(bridge::removeNotificationHandler)
        if (socket != null && remote != null) {
            bridge.closeConnection(socket, remote)
            P2PChannelRegistry.unregister(socket, remote)
        }
    }

    override fun doBeginRead() {
        // No-op
    }

    override fun doWrite(input: ChannelOutboundBuffer) {
        val socket = localSocket ?: throw IllegalStateException("Cannot write before the channel is connected")
        val remote = remoteUser ?: throw IllegalStateException("Cannot write before the channel is connected")

        while (true) {
            val msg = input.current() ?: break
            val buf = msg as? ByteBuf
            if (buf == null) {
                input.remove(IllegalArgumentException("EosP2PChannel only supports ByteBuf messages, got ${msg.javaClass}"))
                continue
            }

            val readable = buf.readableBytes()
            if (readable == 0) {
                input.remove()
                continue
            }

            var remaining = readable
            while (remaining > 0) {
                val chunkSize = remaining.coerceAtMost(MAX_PAYLOAD_BYTES)
                val chunk = ByteBuffer.allocate(chunkSize)
                buf.readBytes(chunk)
                chunk.flip()
                bridge.sendPacket(socket, remote, chunk)
                remaining -= chunkSize
            }

            input.remove()
        }
    }

    internal fun deliverInbound(remote: EosProductUserId, data: ByteBuffer) {
        if (remoteUser != null && remote != remoteUser) return

        val buf = Unpooled.wrappedBuffer(data)
        if (eventLoop().inEventLoop()) {
            firePacket(buf)
        } else {
            eventLoop().execute { firePacket(buf) }
        }
    }

    private fun firePacket(buf: ByteBuf) {
        pipeline().fireChannelRead(buf)
        pipeline().fireChannelReadComplete()
    }

    internal fun updateCongestion(congested: Boolean) {
        unsafe().outboundBuffer()?.setUserDefinedWritability(1, !congested)
    }

    internal fun currentSocket(): EosP2PSocketId? = localSocket

    internal fun currentRemote(): EosProductUserId? = remoteUser
}
