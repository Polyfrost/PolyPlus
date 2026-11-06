package org.polyfrost.polyplus.mixin.client.network;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.polyfrost.polyplus.client.lobby.LobbyCapture;
import org.polyfrost.polyplus.client.resourcepack.PackHttpBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class MixinClientCommonPacketListenerImpl {
    @Shadow @Final protected Connection connection;

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"))
    private void polyplus$noteServerPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        LobbyCapture.onServerPack(connection);
    }

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
import net.minecraft.network.Connection;
import net.minecraft.network.packet.s2c.play.ResourcePackS2CPacket;
import org.polyfrost.polyplus.client.lobby.LobbyCapture;
import org.polyfrost.polyplus.client.resourcepack.PackHttpBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientCommonPacketListenerImpl {
    @Shadow @Final private Connection connection;

    @Inject(method = "handleResourcePack", at = @At("HEAD"))
    private void polyplus$noteServerPack(ResourcePackS2CPacket packet, CallbackInfo ci) {
        LobbyCapture.onServerPack(connection);
    }

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
