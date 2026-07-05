package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class Mixin_ReplaceMainMenu {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void polyplus$replaceMainMenu(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PolyPlusMainMenuScreen) {
            return;
        }
        mc.setScreen(new PolyPlusMainMenuScreen());
        ci.cancel();
    }
}
