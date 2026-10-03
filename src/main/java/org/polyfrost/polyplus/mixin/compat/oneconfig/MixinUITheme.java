package org.polyfrost.polyplus.mixin.compat.oneconfig;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.polyfrost.oneconfig.internal.ui.themes.UIBranding;
import org.polyfrost.oneconfig.internal.ui.themes.UITheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// overrides the getter instead of copying themes, since UITheme.copy's signature changes whenever OneConfig adds a theme property
@Mixin(value = UITheme.class, remap = false)
public class MixinUITheme {
    @Unique
    private static final UIBranding polyplus$BRANDING = new UIBranding("assets/polyplus/brand/oneclient.svg");

    @ModifyReturnValue(method = "getBranding", at = @At("RETURN"), remap = false)
    private UIBranding polyplus$applyOneClientBranding(UIBranding original) {
        return polyplus$BRANDING;
    }
}
