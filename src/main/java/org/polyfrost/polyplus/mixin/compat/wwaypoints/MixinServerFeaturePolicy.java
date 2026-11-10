package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if wwaypoints {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.wwaypoints.protocol.WaypointsFeature;
import org.polyfrost.polyplus.compat.WWaypointsCompat;
import org.spongepowered.asm.mixin.injection.At;
//?}

@Pseudo
@Mixin(targets = "com.wwaypoints.client.ServerFeaturePolicy", remap = false)
public class MixinServerFeaturePolicy {
    //? if wwaypoints {
    // Hypixel doesn't send wWaypoints' server-side opt-out, so it has to be enforced on the client
    @ModifyReturnValue(method = "disabledMask", at = @At("RETURN"), remap = false)
    private static int restrictSneakAndSignModificationsOnHypixel(int original) {
        if (!WWaypointsCompat.isOnHypixel()) return original;
        return original | WaypointsFeature.SNEAK_MODIFICATIONS.mask() | WaypointsFeature.SIGN_MODIFICATIONS.mask();
    }

    // 0.8.0's allows reads the mask field rather than going through disabledMask
    @ModifyReturnValue(method = "allows", at = @At("RETURN"), remap = false)
    private static boolean blockSneakAndSignModificationsOnHypixel(boolean original, @Local(argsOnly = true) WaypointsFeature feature) {
        boolean restricted = feature == WaypointsFeature.SNEAK_MODIFICATIONS || feature == WaypointsFeature.SIGN_MODIFICATIONS;
        return original && !(restricted && WWaypointsCompat.isOnHypixel());
    }
    //?}
}
