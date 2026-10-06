package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if wwaypoints {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wwaypoints.protocol.WaypointsFeature;
//?}

@Pseudo
@Mixin(targets = "com.wwaypoints.client.ServerPolicyFeedback", remap = false)
public class MixinServerPolicyFeedback {
    //? if wwaypoints {
    // the server disallowing what Poly+ already disables changes nothing, so it isn't worth a toast
    @WrapMethod(method = "policyChanged", remap = false)
    private static void ignoreFeaturesDisabledByPolyPlus(int previousMask, int mask, Operation<Void> original) {
        int disabledByPolyPlus = WaypointsFeature.SNEAK_MODIFICATIONS.mask() | WaypointsFeature.SIGN_MODIFICATIONS.mask();
        original.call(previousMask & ~disabledByPolyPlus, mask & ~disabledByPolyPlus);
    }
    //?}
}
