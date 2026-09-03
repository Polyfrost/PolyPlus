package org.polyfrost.polyplus.mixin.compat.polytone;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.polytone.platform.PolytoneFabric", remap = false)
public class MixinPolytoneFabric {
    @Inject(method = "addToProfiles", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$particleHitboxesOffByDefault(CallbackInfo ci) {
        ci.cancel();
    }
}
