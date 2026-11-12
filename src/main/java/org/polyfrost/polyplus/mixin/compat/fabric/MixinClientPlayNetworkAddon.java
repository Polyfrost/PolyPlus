package org.polyfrost.polyplus.mixin.compat.fabric;

//? if > 1.8.9 {
import org.polyfrost.polyplus.client.lobby.LobbyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon", remap = false)
public class MixinClientPlayNetworkAddon {
    @Inject(method = "invokeInitEvent", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void polyplus$quietLobbyConnection(CallbackInfo ci) {
        if (LobbyCompat.quietConnection()) ci.cancel();
    }
}
//?}
