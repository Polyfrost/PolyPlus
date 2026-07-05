package org.polyfrost.polyplus.mixin.client;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class Mixin_MainMenuFpsLimit {
    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void polyplus$mainMenuFpsLimit(CallbackInfoReturnable<Integer> cir) {
        if (Minecraft.getInstance().screen instanceof PolyPlusMainMenuScreen) {
            cir.setReturnValue(PolyPlusConfig.activeMainMenuFpsLimit());
        }
    }
}
