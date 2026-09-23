package org.polyfrost.polyplus.test

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.DefaultEventLoop
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import org.polyfrost.polyplus.client.network.p2p.EosP2PChannel
import org.polyfrost.polyplus.client.network.p2p.EosP2PServerChannel
import org.polyfrost.polyplus.client.network.p2p.P2PChannelRegistry
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class EosP2PAcceptTest {
    private val socket = EosP2PSocketId("polyplus-session")
    private val peer = EosProductUserId("0002eac")

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `an accepted peer's packets are only routed once the channel can take them`() {
        val bridge = EosSdkBridge()
        EosP2PChannel.Holder.bridge = bridge
        val loop = DefaultEventLoop()
        try {
            val child = EosP2PChannel(EosP2PServerChannel())
            val received = CompletableFuture<ByteArray>()
            child.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                    val buf = msg as ByteBuf
                    received.complete(ByteArray(buf.readableBytes()).also(buf::readBytes))
                    buf.release()
                }
            })

            child.setupAccepted(socket, peer)
            assertNull(P2PChannelRegistry.get(socket, peer), "the tick thread would route packets to a channel without an event loop")

            loop.register(child)
            // like the tick thread, deliver the moment the channel shows up
            while (P2PChannelRegistry.get(socket, peer) == null) Thread.onSpinWait()
            child.deliverInbound(peer, ByteBuffer.wrap(byteArrayOf(1, 2, 3)))

            assertArrayEquals(byteArrayOf(1, 2, 3), received.get(5, TimeUnit.SECONDS))
            child.close().sync()
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS)
            EosP2PChannel.Holder.bridge = null
            bridge.shutdown()
        }
    }
}
