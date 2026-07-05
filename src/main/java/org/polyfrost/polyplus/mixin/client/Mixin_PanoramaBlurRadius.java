package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreenKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class Mixin_PanoramaBlurRadius {
    private static final int POLYPLUS_PANORAMA_BLUR_RADIUS = 7;

    @ModifyExpressionValue(
        //? if >= 1.21.11 {
        method = "render",
        //?} else {
        /*method = "processBlurEffect",
        *///?}
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I")
    )
    private int polyplus$forcePanoramaBlurRadius(int original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PolyPlusMainMenuScreen && PolyPlusMainMenuScreenKt.mainMenuPanoramaEnabled()) {
            return POLYPLUS_PANORAMA_BLUR_RADIUS;
        }
        return original;
    }
}
