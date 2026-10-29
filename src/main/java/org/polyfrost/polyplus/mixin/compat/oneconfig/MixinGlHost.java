package org.polyfrost.polyplus.mixin.compat.oneconfig;

import org.spongepowered.asm.mixin.Mixin;

//? if < 1.21.11 {
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

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.GlHost", remap = false)
public class MixinGlHost {
    //? if < 1.21.11 {
    /*@Inject(method = "draw(I)V", at = @At("HEAD"), remap = false)
    private void polyplus$renderPlayerPreviews(int fbo, CallbackInfo ci) {
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
