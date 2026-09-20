package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;

//? if = 26.1 {
/*import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewOverlay;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

@Mixin(RenderTarget.class)
public class MixinRenderTarget {
    //? if >= 26.1 && < 26.2 {
    /*@Inject(method = "blitToScreen", at = @At("HEAD"))
    private void polyplus$renderPreviewOverlay(CallbackInfo ci) {
        RenderTarget self = (RenderTarget) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || self != mc.getMainRenderTarget()) return;
        PlayerPreviewOverlay.renderAll(self);
    }
    *///?}
}
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public class MixinRenderTarget {
    @Inject(method = "draw(II)V", at = @At("HEAD"))
    private void polyplus$renderPreviewOverlay(int width, int height, CallbackInfo ci) {
        RenderTarget self = (RenderTarget) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || self != mc.getRenderTarget()) return;
        PlayerPreviewOverlay.renderAll(self);
    }
}
*///?}
