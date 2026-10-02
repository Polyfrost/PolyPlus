package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Pseudo
@Mixin(targets = "com.wwaypoints.client.HideWaypointCycle", remap = false)
public class MixinHideWaypointCycle {
    @Unique
    private static int[] polyplus$lastRaw;
    @Unique
    private static int polyplus$lastLegacy;
    @Unique
    private static int[] polyplus$lastCycle;

    @WrapOperation(method = "activeThreshold", at = @At(value = "INVOKE", target = "Lcom/wwaypoints/client/HideWaypointCycle;normalize([II)[I"), remap = false)
    private static int[] polyplus$reuseCycle(int[] raw, int legacy, Operation<int[]> original) {
        if (polyplus$lastCycle != null && legacy == polyplus$lastLegacy && Arrays.equals(raw, polyplus$lastRaw)) return polyplus$lastCycle;
        polyplus$lastRaw = raw == null ? null : raw.clone();
        polyplus$lastLegacy = legacy;
        return polyplus$lastCycle = original.call(raw, legacy);
    }
}
