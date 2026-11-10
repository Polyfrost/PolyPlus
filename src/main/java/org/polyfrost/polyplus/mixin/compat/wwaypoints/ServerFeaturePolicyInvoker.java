package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if wwaypoints
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.wwaypoints.client.ServerFeaturePolicy", remap = false)
public interface ServerFeaturePolicyInvoker {
    //? if wwaypoints {
    @Invoker(value = "notifyListeners", remap = false)
    static void polyplus$notifyListeners() {
        throw new AssertionError();
    }
    //?}
}
