package org.polyfrost.polyplus.test

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.DefaultEventLoop
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress
import org.polyfrost.polyplus.client.network.p2p.EosP2PChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class EosP2PSendFailureTest {
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `a packet EOS couldn't send closes the channel instead of leaving a gap in the stream`() {
        // a shut-down bridge refuses every send, like one whose login or platform went away mid-session
        val bridge = EosSdkBridge().apply { shutdown() }
        EosP2PChannel.Holder.bridge = bridge
        val loop = DefaultEventLoop()
        try {
            val channel = EosP2PChannel()
            val caught = CompletableFuture<Throwable>()
            channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                    caught.complete(cause)
                }
            })
            loop.register(channel).sync()
            channel.connect(EosP2PAddress(EosProductUserId("0002eac"), EosP2PSocketId("polyplus-session"))).sync()

            channel.writeAndFlush(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))

            val cause = caught.get(5, TimeUnit.SECONDS)
            assertTrue(cause.message.orEmpty().contains("shutting down"), "unexpected failure reason: $cause")
            assertTrue(channel.closeFuture().await(5, TimeUnit.SECONDS), "the channel stayed open after a dropped send")
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS)
            EosP2PChannel.Holder.bridge = null
        }
    }
}
