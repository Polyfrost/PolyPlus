package org.polyfrost.polyplus.mixin.compat.waveycapes;

import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.tr7zw.waveycapes.renderlayers.CustomCapeRenderLayer", remap = false)
public class MixinCustomCapeRenderLayer {
    //? if >= 1.21.10 {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$skipForDirectPreview(CallbackInfo ci) {
        if (PlayerPreviewRenderer.isDirectPreview()) ci.cancel();
    }
    //?}
}
