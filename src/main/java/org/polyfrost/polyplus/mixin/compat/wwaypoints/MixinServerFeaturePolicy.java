package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if wwaypoints {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.wwaypoints.protocol.WaypointsFeature;
import org.spongepowered.asm.mixin.injection.At;
//?}

@Pseudo
@Mixin(targets = "com.wwaypoints.client.ServerFeaturePolicy", remap = false)
public class MixinServerFeaturePolicy {
    //? if wwaypoints {
    @ModifyReturnValue(method = "allows", at = @At("RETURN"), remap = false)
    private static boolean blockSneakAndSignModifications(boolean original, @Local(argsOnly = true) WaypointsFeature feature) {
        return original && feature != WaypointsFeature.SNEAK_MODIFICATIONS && feature != WaypointsFeature.SIGN_MODIFICATIONS;
    }
    //?}
}
