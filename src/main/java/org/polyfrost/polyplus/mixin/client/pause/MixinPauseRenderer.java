package org.polyfrost.polyplus.mixin.client.pause;

import org.polyfrost.polyplus.client.gui.pausescreen.PauseStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if > 1.8.9 {
import net.minecraft.client.renderer.GameRenderer;
//?} else {
/*import net.minecraft.client.render.GameRenderer;
*///?}

@Mixin(GameRenderer.class)
public class MixinPauseRenderer {
    //? if > 1.8.9 {
    @Inject(method = "render", at = @At("HEAD"))
    private void polyplus$stepPauseStage(CallbackInfo ci) {
        PauseStage.frame();
    }

    @Inject(method = {"bobHurt", "bobView"}, at = @At("HEAD"), cancellable = true)
    private void polyplus$stillPauseCamera(CallbackInfo ci) {
        if (PauseStage.staged()) ci.cancel();
    }
    //?} else {
    /*@Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void polyplus$stepPauseStage(float tickDelta, long startTime, CallbackInfo ci) {
        PauseStage.frame();
    }

    @Inject(method = {"applyHurtCam", "applyViewBobbing"}, at = @At("HEAD"), cancellable = true)
    private void polyplus$stillPauseCamera(float tickDelta, CallbackInfo ci) {
        if (PauseStage.staged()) ci.cancel();
    }

    @Inject(method = "transformCamera", at = @At("HEAD"), cancellable = true)
    private void polyplus$pauseCamera(float tickDelta, CallbackInfo ci) {
        if (PauseStage.applyLegacyCamera(tickDelta)) ci.cancel();
    }
    *///?}
}
