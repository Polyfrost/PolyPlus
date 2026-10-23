package org.polyfrost.polyplus.mixin.compat.oneconfig;

import androidx.navigation.NavGraphBuilder;
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.navigation.NavigationKt", remap = false)
public class MixinNavigationKt {
    @Inject(method = "navigation", at = @At("TAIL"), remap = false)
    private static void polyplus$addCosmeticsRoute(NavGraphBuilder builder, CallbackInfo ci) {
        PolyPlusOneConfigIntegration.addRoutes(builder);
    }
}
