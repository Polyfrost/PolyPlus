package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
import com.mojang.blaze3d.audio.Channel;
import org.polyfrost.polyplus.client.lobby.LobbySoundGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class MixinChannel {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void polyplus$holdLobbyStart(CallbackInfo ci) {
        if (LobbySoundGate.play((Channel) (Object) this)) ci.cancel();
    }
}
//?}
