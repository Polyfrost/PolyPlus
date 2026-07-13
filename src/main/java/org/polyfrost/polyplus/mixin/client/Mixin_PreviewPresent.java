//? if >= 26.1 && < 26.2 {
package org.polyfrost.polyplus.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public class Mixin_PreviewPresent {
    @Inject(method = "blitToScreen", at = @At("HEAD"))
    private void polyplus$renderPreviewOverlay(CallbackInfo ci) {
        RenderTarget self = (RenderTarget) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || self != mc.getMainRenderTarget()) return;
        PlayerPreviewOverlay.renderAll(self);
    }
}
//?}

