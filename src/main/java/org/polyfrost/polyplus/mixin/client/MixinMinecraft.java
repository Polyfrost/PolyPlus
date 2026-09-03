package org.polyfrost.polyplus.mixin.client;

//? if >= 26.2 {
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewOverlay;
//?}
import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 1500)
public class MixinMinecraft {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void polyplus$beginMenuBackdropFrame(boolean renderLevel, CallbackInfo ci) {
        MenuPanorama.beginPass();
    }

    //? if >= 26.2 {
    @Inject(
        method = "renderFrame",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"
        )
    )
    private void polyplus$renderPreviewOverlay(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        RenderTarget rt = mc.gameRenderer.mainRenderTarget();
        if (rt != null) PlayerPreviewOverlay.renderAll(rt);
    }
    //?}
}
