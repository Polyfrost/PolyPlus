package org.polyfrost.polyplus.mixin.client.network;

import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.ClientManager", remap = false)
public class Mixin_VoicechatResolveP2PAddress {

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void polyplus$resolveP2PAddress(SocketAddress socketAddress, CallbackInfoReturnable<String> cir) {
        if (socketAddress instanceof EosP2PAddress) {
            cir.setReturnValue("127.0.0.1");
        }
    }
}
