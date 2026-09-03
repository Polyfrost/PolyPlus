package org.polyfrost.polyplus.mixin.client.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ServerChannel;
import java.net.InetAddress;
import net.minecraft.server.network.ServerConnectionListener;
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId;
import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress;
import org.polyfrost.polyplus.client.network.p2p.EosP2PServerChannel;
import org.polyfrost.polyplus.client.network.p2p.P2PListenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerConnectionListener.class)
public abstract class MixinServerConnectionListener {
    @WrapOperation(
        method = "startTcpServerListener",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/bootstrap/ServerBootstrap;channel(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;"
        )
    )
    private AbstractBootstrap polyplus$redirectChannel(
        ServerBootstrap bootstrap,
        Class<? extends ServerChannel> channelClass,
        Operation<AbstractBootstrap> original
    ) {
        if (P2PListenContext.hasPendingListen()) {
            return original.call(bootstrap, EosP2PServerChannel.class);
        }
        return original.call(bootstrap, channelClass);
    }

    @WrapOperation(
        method = "startTcpServerListener",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/bootstrap/ServerBootstrap;localAddress(Ljava/net/InetAddress;I)Lio/netty/bootstrap/AbstractBootstrap;"
        )
    )
    private AbstractBootstrap polyplus$redirectLocalAddress(
        ServerBootstrap bootstrap,
        InetAddress address,
        int port,
        Operation<AbstractBootstrap> original
    ) {
        EosP2PSocketId socket = P2PListenContext.consumeSocketOverride();
        if (socket != null) {
            return bootstrap.localAddress(new EosP2PAddress(P2PListenContext.requireLocalUser(), socket));
        }
        return original.call(bootstrap, address, port);
    }
}
