package org.polyfrost.polyplus.mixin.client;

//? if >= 1.21.4 {
import com.mojang.blaze3d.platform.FramerateLimitTracker;
//?}
import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.features.AdaptiveBlurDefaults;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >= 1.21.4 {
@Mixin(FramerateLimitTracker.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class Mixin_MainMenuFpsLimit {
    private static final int UNCAPPED = 260;

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void polyplus$mainMenuFpsLimit(CallbackInfoReturnable<Integer> cir) {
        if (AdaptiveBlurDefaults.isSampling()) {
            cir.setReturnValue(UNCAPPED);
            return;
        }
        //? if >= 26.2 {
        /*Object screen = Minecraft.getInstance().gui.screen();
        *///?} else {
        Object screen = Minecraft.getInstance().screen;
        //?}
        if (screen instanceof PolyPlusMainMenuScreen) {
            cir.setReturnValue(PolyPlusConfig.activeMainMenuFpsLimit());
        }
    }
}
