package org.polyfrost.polyplus.mixin.client.network;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.polyfrost.polyplus.client.resourcepack.PackHttpBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientCommonPacketListenerImpl.class)
public class MixinClientCommonPacketListenerImpl {
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
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import org.polyfrost.polyplus.client.resourcepack.PackHttpBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientCommonPacketListenerImpl {
    @ModifyExpressionValue(
        method = "handleResourcePack",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/packet/s2c/play/ResourcePackS2CPacket;getUrl()Ljava/lang/String;"
        )
    )
    private String polyplus$redirectP2PPackUrl(String original) {
        String rewritten = PackHttpBridge.INSTANCE.rewrite(original);
        return rewritten != null ? rewritten : original;
    }
}
*///?}
