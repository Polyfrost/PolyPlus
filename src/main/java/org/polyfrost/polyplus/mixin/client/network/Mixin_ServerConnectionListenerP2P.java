package org.polyfrost.polyplus.mixin.client.network;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ServerChannel;
import net.minecraft.server.network.ServerConnectionListener;
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId;
import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress;
import org.polyfrost.polyplus.client.network.p2p.EosP2PServerChannel;
import org.polyfrost.polyplus.client.network.p2p.P2PListenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerConnectionListener.class)
public abstract class Mixin_ServerConnectionListenerP2P {

    @Redirect(
        method = "startTcpServerListener",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/bootstrap/ServerBootstrap;channel(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;"
        )
    )
    private AbstractBootstrap polyplus$redirectChannel(ServerBootstrap bootstrap, Class<? extends ServerChannel> channelClass) {
        if (P2PListenContext.hasPendingListen()) {
            return bootstrap.channel(EosP2PServerChannel.class);
        }
        return bootstrap.channel(channelClass);
    }

    @Redirect(
        method = "startTcpServerListener",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/bootstrap/ServerBootstrap;localAddress(Ljava/net/InetAddress;I)Lio/netty/bootstrap/AbstractBootstrap;"
        )
    )
    private AbstractBootstrap polyplus$redirectLocalAddress(ServerBootstrap bootstrap, java.net.InetAddress address, int port) {
        EosP2PSocketId socket = P2PListenContext.consumeSocketOverride();
        if (socket != null) {
            return bootstrap.localAddress(new EosP2PAddress(
                P2PListenContext.requireLocalUser(), socket));
        }
        return bootstrap.localAddress(address, port);
    }
}
