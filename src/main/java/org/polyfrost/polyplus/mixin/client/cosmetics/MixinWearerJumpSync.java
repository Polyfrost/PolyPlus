package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if >= 1.21.4 {
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import org.polyfrost.polyplus.client.cosmetics.render.WearerJumps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinWearerJumpSync {
    @Inject(
        method = "handleEntityPositionSync",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread", shift = At.Shift.AFTER)
    )
    private void polyplus$positionSyncBreaksRings(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        WearerJumps.entitySynced(packet);
    }
}
//?}
