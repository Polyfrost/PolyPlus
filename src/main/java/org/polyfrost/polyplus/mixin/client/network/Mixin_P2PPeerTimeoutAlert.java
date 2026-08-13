package org.polyfrost.polyplus.mixin.client.network;

import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.polyfrost.polyplus.client.host.P2PPeerAlerts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class Mixin_P2PPeerTimeoutAlert {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void polyplus$alertHostOnTimeout(DisconnectionDetails details, CallbackInfo ci) {
        P2PPeerAlerts.onPlayerDisconnected(
            ((ServerCommonPacketListenerAccessor) this).getPolyplusConnection().getRemoteAddress(),
            player.getName().getString(),
            details.reason()
        );
    }
}
