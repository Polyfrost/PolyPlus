package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class Mixin_ReplaceMainMenu {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void polyplus$replaceMainMenu(CallbackInfo ci) {
        if (PolyPlusConfig.getUseVanillaMainMenu()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        //? if >= 26.2 {
        /*if (mc.gui.screen() instanceof PolyPlusMainMenuScreen) {
            return;
        }
        mc.gui.setScreen(new PolyPlusMainMenuScreen());
        *///?} else {
        if (mc.screen instanceof PolyPlusMainMenuScreen) {
            return;
        }
        mc.setScreen(new PolyPlusMainMenuScreen());
        //?}
        ci.cancel();
    }
}
