package org.polyfrost.polyplus.mixin.client.network;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.polyfrost.polyplus.client.resourcepack.PackHttpBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientCommonPacketListenerImpl.class)
public class Mixin_P2PResourcePackUrl {
    @ModifyExpressionValue(
        method = "handleResourcePackPush",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/common/ClientboundResourcePackPushPacket;url()Ljava/lang/String;"
        )
    )
    private String polyplus$redirectP2PPackUrl(String original) {
        String rewritten = PackHttpBridge.INSTANCE.rewrite(original);
        return rewritten != null ? rewritten : original;
    }
}
