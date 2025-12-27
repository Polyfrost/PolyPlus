package org.polyfrost.polyplus.mixin.client;

import dev.deftu.omnicore.api.OmniCore;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.polyfrost.polyplus.client.events.ReceivePacketEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public class Mixin_ReceivePacketEvent {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void polyplus$onPacketReceived(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        ReceivePacketEvent event = new ReceivePacketEvent(packet);
        OmniCore.getEventBus().post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
