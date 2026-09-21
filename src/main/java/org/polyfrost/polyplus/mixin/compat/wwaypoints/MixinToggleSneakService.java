package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.wwaypoints.client.ToggleSneakService", remap = false)
public class MixinToggleSneakService {
    @ModifyReturnValue(method = "staySneakedInContainersEnabled", at = @At("RETURN"), remap = false)
    private static boolean polyplus$forceOff(boolean original) {
        return false;
    }
}
