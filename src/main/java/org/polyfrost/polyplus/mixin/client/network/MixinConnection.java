package org.polyfrost.polyplus.mixin.client.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.polyfrost.polyplus.client.network.p2p.EosP2PChannel;
import org.polyfrost.polyplus.client.network.p2p.P2PConnectionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(value = Connection.class, priority = 1001)
public abstract class MixinConnection {
    @WrapOperation(
        method = "connect",
        at = @At(
            value = "INVOKE",
            target = "channel(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;"
        ),
        require = 0
    )
    private static AbstractBootstrap polyplus$redirectChannel(
        @Coerce AbstractBootstrap<?, ?> bootstrap,
        Class<? extends Channel> channelClass,
        Operation<AbstractBootstrap> original
    ) {
        if (P2PConnectionContext.hasPendingJoin()) {
            return original.call(bootstrap, EosP2PChannel.class);
        }
        return original.call(bootstrap, channelClass);
    }
}
