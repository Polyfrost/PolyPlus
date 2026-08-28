package org.polyfrost.polyplus.mixin.compat.oneconfig;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import org.polyfrost.oneconfig.internal.ui.navigation.NavigationGroup;
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.navigation.RoutesKt", remap = false)
public class MixinRoutesKt {
    @ModifyReturnValue(method = "getNavigationGroups", at = @At("RETURN"), remap = false)
    private static List<NavigationGroup> polyplus$addCosmeticsNavigation(List<NavigationGroup> original) {
        return PolyPlusOneConfigIntegration.navigationGroups(original);
    }
}
