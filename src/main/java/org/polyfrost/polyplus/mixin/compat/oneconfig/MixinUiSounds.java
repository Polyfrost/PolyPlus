package org.polyfrost.polyplus.mixin.compat.oneconfig;

import org.polyfrost.polyplus.client.lobby.LobbyAmbience;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.sound.UiSounds", remap = false)
public class MixinUiSounds {
    @Inject(method = "startAmbienceIfEnabled", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$yieldToLobby(CallbackInfo ci) {
        if (LobbyAmbience.holdsMusic()) ci.cancel();
    }
}
