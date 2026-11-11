package org.polyfrost.polyplus.mixin.compat.dynamicfps;

import org.polyfrost.polyplus.compat.DynamicFpsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
//? if > 1.8.9 {
@Mixin(targets = "dynamic_fps.impl.DynamicFPSMod", remap = false)
//?} else {
/*@Mixin(targets = "net.lostluma.dynamic_fps.impl.fabric.DynamicFPSMod", remap = false)
*///?}
public class MixinDynamicFPSMod {
    //? if = 1.8.9 {
    /*@Inject(method = "powerState", at = @At("HEAD"), remap = false, require = 0, expect = 0)
    private static void polyplus$trackAnimationsLegacy(CallbackInfoReturnable<Object> cir) {
        DynamicFpsCompat.frame();
    }
    *///?}

    @Inject(method = "checkForRender()Z", at = @At("HEAD"), remap = false, require = 0, expect = 0)
    private static void polyplus$trackAnimations(CallbackInfoReturnable<Boolean> cir) {
        DynamicFpsCompat.frame();
    }

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$holdDuringAnimations(CallbackInfoReturnable<Boolean> cir) {
        if (DynamicFpsCompat.holding()) cir.setReturnValue(true);
    }
}
