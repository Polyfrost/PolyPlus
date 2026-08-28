package org.polyfrost.polyplus.mixin.compat.oneconfig;

import androidx.compose.runtime.snapshots.SnapshotStateList;
import org.jetbrains.annotations.Nullable;
import org.polyfrost.oneconfig.internal.ui.themes.ThemeRegistry;
import org.polyfrost.oneconfig.internal.ui.themes.UIBranding;
import org.polyfrost.oneconfig.internal.ui.themes.UITheme;
import org.polyfrost.polyplus.client.ThemeBrandingUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ThemeRegistry.class, remap = false)
public class MixinThemeRegistry {
    @Unique
    private static final String ONECLIENT_LOGO = "assets/polyplus/brand/oneclient.svg";

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void polyplus$applyOneClientBranding(CallbackInfo ci) {
        UIBranding branding = new UIBranding(ONECLIENT_LOGO);
        SnapshotStateList<UITheme> registry = ThemeRegistry.INSTANCE.getRegistry$internal();

        for (int i = 0; i < registry.size(); i++) {
            registry.set(i, ThemeBrandingUtil.withBranding(registry.get(i), branding));
        }

        @Nullable UITheme active = ThemeRegistry.INSTANCE.getActiveTheme$internal();
        if (active != null) {
            ThemeRegistry.INSTANCE.activate(ThemeBrandingUtil.withBranding(active, branding));
        }
    }
}
