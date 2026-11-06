package org.polyfrost.polyplus.mixin.compat.blur;

import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "eu.midnightdust.blur.BlurRenderer", remap = false)
public class MixinBlurRenderer {
    @Inject(method = "shouldBlur", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$keepLobbySharp(CallbackInfoReturnable<Boolean> cir) {
        if (LobbyScene.showing()) cir.setReturnValue(false);
    }
}
