package org.polyfrost.polyplus.mixin.compat.distanthorizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if dh {
import org.polyfrost.polyplus.compat.distanthorizons.DhLobbyGuard;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.commonMixins.MixinVanillaFogCommon", remap = false)
public abstract class MixinDhVanillaFog {
    //? if dh {
    @Inject(method = "cancelFog()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$keepLobbyFog(CallbackInfoReturnable<Boolean> cir) {
        if (DhLobbyGuard.skipRender()) {
            cir.setReturnValue(false);
        }
    }
    //?}
}
