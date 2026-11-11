package org.polyfrost.polyplus.mixin.compat.distanthorizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if dh {
import org.polyfrost.polyplus.compat.distanthorizons.DhLobbyGuard;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public abstract class MixinDhClientApi {
    //? if dh {
    @Inject(method = "renderLods()V", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$skipLobbyLods(CallbackInfo ci) {
        if (DhLobbyGuard.skipRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDeferredLodsForShaders()V", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$skipLobbyDeferredLods(CallbackInfo ci) {
        if (DhLobbyGuard.skipRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFadeOpaque()V", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$skipLobbyFadeOpaque(CallbackInfo ci) {
        if (DhLobbyGuard.skipRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFadeTransparent()V", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$skipLobbyFadeTransparent(CallbackInfo ci) {
        if (DhLobbyGuard.skipRender()) {
            ci.cancel();
        }
    }
    //?}
}
