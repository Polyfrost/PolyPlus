package org.polyfrost.polyplus.mixin.compat.oneconfig;

import org.spongepowered.asm.mixin.Mixin;

//? if < 26.1 {
/*import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewOffscreen;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget;
//?} else {
/^import net.minecraft.client.render.pipeline.RenderTarget;
^///?}
*///?}

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx", remap = false)
public class MixinSkiaCtx {
    //? if < 26.1 {
    /*// draw() returns before its TAIL on frames without Skia work, so released previews are disposed from its HEAD too
    @Inject(method = "draw", at = @At("HEAD"), remap = false)
    private void polyplus$disposeReleasedPreviews(CallbackInfo ci) {
        PlayerPreviewOffscreen.disposeReleased();
    }

    @Inject(method = "draw", at = @At("TAIL"), remap = false)
    private void polyplus$renderPlayerPreviews(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        //? if > 1.8.9 {
        RenderTarget rt = mc.getMainRenderTarget();
        //?} else {
        /^RenderTarget rt = mc.getRenderTarget();
        ^///?}
        if (rt != null) PlayerPreviewOffscreen.renderAll(rt);
    }
    *///?}
}
