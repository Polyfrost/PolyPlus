package org.polyfrost.polyplus.mixin.compat.rrls;

//? if >= 26.2 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.polyfrost.polyplus.compat.RrlsCrashGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "org.redlance.dima_dencep.mods.rrls.utils.OverlayHelper", remap = false)
public class MixinOverlayHelper {
    @ModifyExpressionValue(
            method = "lookupState",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/redlance/dima_dencep/mods/rrls/config/HideType;canHide(Z)Z",
                    remap = false
            ),
            remap = false,
            require = 0,
            expect = 0
    )
    private static boolean polyplus$neverHideOnVulkan(boolean original) {
        return original && !RrlsCrashGuard.active();
    }

    @ModifyReturnValue(method = "isCurrentRenderingState", at = @At("RETURN"), remap = false, require = 0, expect = 0)
    private static boolean polyplus$neverRenderingOnVulkan(boolean original) {
        return original && !RrlsCrashGuard.active();
    }
}
//?}
