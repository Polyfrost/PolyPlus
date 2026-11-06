package org.polyfrost.polyplus.mixin.compat.entityculling;

import org.polyfrost.polyplus.api.lobby.LobbyApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.tr7zw.entityculling.NMSCullingHelper", remap = false)
public class MixinNMSCullingHelper {
    @Inject(method = "ignoresCulling", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$keepLobbyEntities(CallbackInfoReturnable<Boolean> cir) {
        if (LobbyApi.isRendering()) cir.setReturnValue(true);
    }
}
