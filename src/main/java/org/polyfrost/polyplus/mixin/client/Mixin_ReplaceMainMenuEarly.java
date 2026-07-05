package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class Mixin_ReplaceMainMenuEarly {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void polyplus$replaceMainMenuEarly(Screen screen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        boolean opensTitleScreen = screen instanceof TitleScreen || (screen == null && mc.player == null);
        if (!opensTitleScreen) {
            return;
        }

        if (!(mc.screen instanceof PolyPlusMainMenuScreen)) {
            mc.setScreen(new PolyPlusMainMenuScreen());
        }
        ci.cancel();
    }
}
