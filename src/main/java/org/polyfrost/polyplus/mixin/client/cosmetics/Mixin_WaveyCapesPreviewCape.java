//? if >= 1.21.10 {
package org.polyfrost.polyplus.mixin.client.cosmetics;

import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.tr7zw.transition.mc.entitywrapper.PlayerWrapper", remap = false)
public class Mixin_WaveyCapesPreviewCape {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getCapeTexture", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$previewCapeTexture(CallbackInfoReturnable cir) {
        if (PlayerPreviewRenderer.isRenderingPreview()) {
            cir.setReturnValue(PlayerPreviewRenderer.previewCapeOverride());
        }
    }
}
//?}
