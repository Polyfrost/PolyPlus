package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig;
import org.polyfrost.polyplus.client.features.OnboardingFeatures;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.polyfrost.polyplus.client.gui.PolyPlusOnboardingScreen;
import org.polyfrost.polyplus.privacy.PrivacyConsent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class Mixin_ReplaceMainMenu {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void polyplus$replaceMainMenu(CallbackInfo ci) {
        if (PolyPlusMainMenuConfig.getUseVanillaMainMenu()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        //? if >= 26.2 {
        /*if (mc.gui.screen() instanceof PolyPlusMainMenuScreen || mc.gui.screen() instanceof PolyPlusOnboardingScreen) {
            return;
        }
        mc.gui.setScreen(polyplus$needsOnboarding() ? new PolyPlusOnboardingScreen() : new PolyPlusMainMenuScreen());
        *///?} else {
        if (mc.screen instanceof PolyPlusMainMenuScreen || mc.screen instanceof PolyPlusOnboardingScreen) {
            return;
        }
        mc.setScreen(polyplus$needsOnboarding() ? new PolyPlusOnboardingScreen() : new PolyPlusMainMenuScreen());
        //?}
        ci.cancel();
    }

    @Unique
    private static boolean polyplus$needsOnboarding() {
        return !PolyPlusConfig.getOnboardingCompleted()
                || PrivacyConsent.needsPrompt()
                || OnboardingFeatures.needsMotionBlurChoice();
    }
}
