package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if wwaypoints {
import com.wwaypoints.client.ModConfig;
import org.polyfrost.polyplus.compat.WWaypointsCompat;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Pseudo
@Mixin(targets = "com.wwaypoints.client.ConfigStorage", remap = false)
public class MixinConfigStorage {
    //? if wwaypoints {
    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private static void persistDisabledFeatures(ModConfig config, CallbackInfo ci) {
        if (config != null) WWaypointsCompat.disableFeatures(config);
    }
    //?}
}
