package org.polyfrost.polyplus.mixin.client.euphoria;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.euphoriapatches.euphoria_patcher.integration.SodiumConsole", remap = false)
public class Mixin_HideEuphoriaPatcherMessages {
    @Inject(
            method = "logMessage(IILjava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0,
            expect = 0
    )
    private static void polyplus$hidePopupMessages(int level, int messageFadeTimer, String message, CallbackInfo ci) {
        ci.cancel();
    }
}
