package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.ProgressRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.pipeline.RenderTarget;
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProgressRenderer.class)
public class MixinProgressRenderer {
    @Shadow
    private Minecraft minecraft;

    @ModifyExpressionValue(method = "progressStagePercentage", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/ProgressRenderer;target:Lnet/minecraft/client/render/pipeline/RenderTarget;"))
    private RenderTarget polyplus$useMainTarget(RenderTarget original) {
        return PolyPlusMainMenuConfig.getPanoramaInAllMenus() ? this.minecraft.getRenderTarget() : original;
    }

    @Inject(method = "progressStagePercentage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 0, shift = At.Shift.AFTER))
    private void polyplus$drawBackdrop(int percentage, CallbackInfo ci) {
        Window window = new Window(this.minecraft);
        MenuPanorama.drawLoadingBackdrop(window.getWidth(), window.getHeight());
    }
}
*///?}
