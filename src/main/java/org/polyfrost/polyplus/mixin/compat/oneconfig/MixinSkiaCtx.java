package org.polyfrost.polyplus.mixin.compat.oneconfig;

import org.spongepowered.asm.mixin.Mixin;

//? if > 1.8.9 && < 1.21.5 || >= 1.21.8 && < 26.1 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewOverlay;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx", remap = false)
public class MixinSkiaCtx {
    //? if > 1.8.9 && < 1.21.5 || >= 1.21.8 && < 26.1 {
    /*@Inject(method = "draw", at = @At("TAIL"), remap = false)
    private void polyplus$renderPreviewOverlay(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        RenderTarget rt = mc.getMainRenderTarget();
        if (rt != null) PlayerPreviewOverlay.renderAll(rt);
    }
    *///?}
}
