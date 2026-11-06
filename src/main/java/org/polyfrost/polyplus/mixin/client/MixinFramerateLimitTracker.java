package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig;
import org.polyfrost.polyplus.client.features.AdaptiveBlurDefaults;
import org.polyfrost.polyplus.client.gui.MainMenuReplacement;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >= 1.21.4 {
import com.mojang.blaze3d.platform.FramerateLimitTracker;
//?}


//? if = 1.21.1 || = 1.8.9 {
/*import net.minecraft.client.Minecraft;
*///?}

//? if >= 1.21.4 {
@Mixin(FramerateLimitTracker.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class MixinFramerateLimitTracker {
    //? if > 1.8.9 {
    @ModifyReturnValue(method = "getFramerateLimit", at = @At("RETURN"))
    //?} else {
    /*@ModifyReturnValue(method = "getMaxFramerate", at = @At("RETURN"))
    *///?}
    private int polyplus$mainMenuFpsLimit(int original) {
        if (AdaptiveBlurDefaults.isSampling()) {
            return ClientPlatform.INSTANCE.getUnlimitedFramerate();
        }
        if (MainMenuReplacement.isMenu(ClientPlatform.INSTANCE.currentScreen())) {
            return PolyPlusMainMenuConfig.activeMainMenuFpsLimit();
        }
        return original;
    }
}
