package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//? if >= 26.1 {
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
//?}
import net.minecraft.client.renderer.GameRenderer;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreenKt;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Unique
    private static final int POLYPLUS_PANORAMA_BLUR_RADIUS = 7;

    @ModifyExpressionValue(
        //? if >= 26.1 {
        method = "extractOptions",
        //?} elif >= 1.21.8 {
        /*method = "render",
        *///?} else {
        /*method = "processBlurEffect",
        *///?}
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I")
    )
    private int polyplus$forcePanoramaBlurRadius(int original) {
        if (ClientPlatform.INSTANCE.currentScreen() instanceof PolyPlusMainMenuScreen
            && PolyPlusMainMenuScreenKt.mainMenuPanoramaEnabled()) {
            return POLYPLUS_PANORAMA_BLUR_RADIUS;
        }
        return original;
    }

    //? if >= 26.1 {
    @Inject(method = "render", at = @At("HEAD"))
    private void polyplus$dropCoveredPanorama(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (!MenuPanorama.suppressPanorama()) return;

        GameRenderer self = (GameRenderer) (Object) this;
        //? if >= 26.2 {
        GuiRenderState state = self.gameRenderState().guiRenderState;
        //?} else {
        /*GuiRenderState state = self.getGameRenderState().guiRenderState;
        *///?}
        if (state.panoramaRenderState == null) return;
        state.panoramaRenderState = null;

        if (MenuPanorama.backdropFilled()) return;
        //? if >= 26.2 {
        net.minecraft.util.ARGB.setVector4fFromARGB32(state.clearColorOverride, MenuPanorama.BASE_COLOR);
        //?} else {
        /*state.clearColorOverride = MenuPanorama.BASE_COLOR;
        *///?}
    }
    //?}
}
