package org.polyfrost.polyplus.mixin.compat.lambdynlights;

import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.api.lobby.LobbyApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.LambDynLights", remap = false)
public class MixinLambDynLights {
    @Inject(method = "onEndLevelTick", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$skipLobbyTick(CallbackInfo ci) {
        if (polyplus$outsideWorld()) ci.cancel();
    }

    @Inject(method = "addLightSource", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$skipLobbySource(CallbackInfo ci) {
        if (polyplus$outsideWorld()) ci.cancel();
    }

    @Inject(method = "getDynamicLightLevel", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$noLobbyLight(CallbackInfoReturnable<Double> cir) {
        if (polyplus$outsideWorld()) cir.setReturnValue(0.0);
    }

    @Unique
    private static boolean polyplus$outsideWorld() {
        //? if > 1.8.9 {
        return LobbyApi.isRendering() || Minecraft.getInstance().level == null;
        //?} else {
        /*return LobbyApi.isRendering() || Minecraft.getInstance().world == null;
        *///?}
    }
}
