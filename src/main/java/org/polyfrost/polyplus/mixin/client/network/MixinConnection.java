package org.polyfrost.polyplus.mixin.client.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.net.InetAddress;
import net.minecraft.network.Connection;
import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress;
import org.polyfrost.polyplus.client.network.p2p.EosP2PChannel;
import org.polyfrost.polyplus.client.network.p2p.P2PConnectionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Connection.class)
public abstract class MixinConnection {
    @WrapOperation(
        method = "connect",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/bootstrap/Bootstrap;channel(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;"
        )
    )
    private static AbstractBootstrap polyplus$redirectChannel(
        Bootstrap bootstrap,
        Class<? extends Channel> channelClass,
        Operation<AbstractBootstrap> original
    ) {
        if (P2PConnectionContext.hasPendingJoin()) {
            return original.call(bootstrap, EosP2PChannel.class);
        }
        return original.call(bootstrap, channelClass);
    }

    @WrapOperation(
        method = "connect",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;"
        )
    )
    private static ChannelFuture polyplus$redirectConnect(
        Bootstrap bootstrap,
        InetAddress address,
        int port,
        Operation<ChannelFuture> original
    ) {
        EosP2PAddress override = P2PConnectionContext.consumeAddressOverride();
        if (override != null) {
            return bootstrap.connect(override);
        }
        return original.call(bootstrap, address, port);
    }
}
