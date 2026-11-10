package org.polyfrost.polyplus.mixin.client.ping;

import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.ping.PingInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinPingPick {
    //? if >= 26.1 {
    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    //?} elif > 1.8.9 {
    /*@Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    *///?} else {
    /*@Inject(method = "doPick", at = @At("HEAD"), cancellable = true)
    *///?}
    private void polyplus$yieldToPing(CallbackInfo ci) {
        if (PingInput.takesPickBlock()) ci.cancel();
    }
}
