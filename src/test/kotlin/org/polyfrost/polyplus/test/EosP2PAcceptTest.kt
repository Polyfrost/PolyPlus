package org.polyfrost.polyplus.test

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.EventLoop
//? if > 1.8.9 {
import io.netty.channel.DefaultEventLoop
//?} else {
/*import io.netty.channel.local.LocalEventLoopGroup
*///?}
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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

    // a Stonecutter comment placed after a backtick name containing an apostrophe is not toggled,
    // so the version split has to live above the test methods
    //? if > 1.8.9 {
    private fun eventLoop(): EventLoop = DefaultEventLoop()
    //?} else {
    /*private fun eventLoop(): EventLoop = LocalEventLoopGroup(1).next()
    *///?}

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `an accepted peer's packets are only routed once the channel can take them`() {
        val bridge = EosSdkBridge()
        EosP2PChannel.Holder.bridge = bridge
        val loop = eventLoop()
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

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `a replaced channel closing late leaves its peer routed to the new one`() {
        val rejoinSocket = EosP2PSocketId("polyplus-rejoin")
        val bridge = EosSdkBridge()
        EosP2PChannel.Holder.bridge = bridge
        val loop = DefaultEventLoop()
        try {
            val server = EosP2PServerChannel()
            val stale = EosP2PChannel(server).apply { setupAccepted(rejoinSocket, peer) }
            loop.register(stale).sync()
            val rejoined = EosP2PChannel(server).apply { setupAccepted(rejoinSocket, peer) }
            loop.register(rejoined).sync()

            stale.close().sync()
            assertSame(rejoined, P2PChannelRegistry.get(rejoinSocket, peer), "the stale channel unregistered its replacement")

            rejoined.close().sync()
            assertNull(P2PChannelRegistry.get(rejoinSocket, peer), "the last channel for the peer stayed registered")
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS)
            EosP2PChannel.Holder.bridge = null
            bridge.shutdown()
        }
    }
}
