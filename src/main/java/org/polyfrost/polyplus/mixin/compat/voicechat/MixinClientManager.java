package org.polyfrost.polyplus.mixin.compat.voicechat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.net.SocketAddress;
import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.ClientManager", remap = false)
public class MixinClientManager {
    @WrapMethod(method = "resolveAddress", remap = false, require = 0, expect = 0)
    private static String polyplus$resolveP2PAddress(SocketAddress socketAddress, Operation<String> original) {
        if (socketAddress instanceof EosP2PAddress) {
            return "127.0.0.1";
        }
        return original.call(socketAddress);
    }
}
