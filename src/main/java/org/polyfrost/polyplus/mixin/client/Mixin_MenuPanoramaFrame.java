package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class Mixin_MenuPanoramaFrame {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void polyplus$beginMenuBackdropFrame(boolean renderLevel, CallbackInfo ci) {
        MenuPanorama.beginPass();
    }
}
