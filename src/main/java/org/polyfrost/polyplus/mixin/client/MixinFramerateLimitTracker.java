package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//? if >= 1.21.4 {
import com.mojang.blaze3d.platform.FramerateLimitTracker;
//?} else {
/*import net.minecraft.client.Minecraft;
*///?}
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig;
import org.polyfrost.polyplus.client.features.AdaptiveBlurDefaults;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

//? if >= 1.21.4 {
@Mixin(FramerateLimitTracker.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class MixinFramerateLimitTracker {
    @Unique
    private static final int POLYPLUS_UNCAPPED = 260;

    @ModifyReturnValue(method = "getFramerateLimit", at = @At("RETURN"))
    private int polyplus$mainMenuFpsLimit(int original) {
        if (AdaptiveBlurDefaults.isSampling()) {
            return POLYPLUS_UNCAPPED;
        }
        if (ClientPlatform.INSTANCE.currentScreen() instanceof PolyPlusMainMenuScreen) {
            return PolyPlusMainMenuConfig.activeMainMenuFpsLimit();
        }
        return original;
    }
}
