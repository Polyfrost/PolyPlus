package org.polyfrost.polyplus.mixin.client.network;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress;
import org.polyfrost.polyplus.client.network.p2p.EosP2PChannel;
import org.polyfrost.polyplus.client.network.p2p.P2PConnectionContext;
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(Connection.class)
public abstract class Mixin_ConnectionP2P {

    @Redirect(
        method = "connect",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/bootstrap/Bootstrap;channel(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;"
        )
    )
    private static AbstractBootstrap polyplus$redirectChannel(Bootstrap bootstrap, Class<? extends Channel> channelClass) {
        if (P2PConnectionContext.hasPendingJoin()) {
            return bootstrap.channel(EosP2PChannel.class);
        }
        return bootstrap.channel(channelClass);
    }

    @Redirect(
        method = "connect",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;"
        )
    )
    private static ChannelFuture polyplus$redirectConnect(Bootstrap bootstrap, java.net.InetAddress address, int port) {
        EosP2PAddress override = P2PConnectionContext.consumeAddressOverride();
        if (override != null) {
            return bootstrap.connect(override);
        }
        return bootstrap.connect(address, port);
    }
}
