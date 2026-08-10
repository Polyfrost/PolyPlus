//? if < 26.2 {
/*package org.polyfrost.polyplus.mixin.client;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/^import net.minecraft.client.gui.GuiGraphics;
^///?}

import net.minecraft.client.gui.components.TabButton;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TabButton.class)
public class Mixin_MenuPanoramaTabButton {
    //? if >= 26.1 {
    @Inject(method = "extractMenuBackground", at = @At("HEAD"), cancellable = true)
    private void polyplus$keepPanoramaTabBackground(GuiGraphicsExtractor graphics, int x, int y, int right, int bottom, CallbackInfo ci) {
        if (!MenuPanorama.backdropDrawn()) return;
        graphics.fill(x, y, right, bottom, MenuPanorama.LIST_TINT);
        ci.cancel();
    }
    //?} else {
    /^@Inject(method = "renderMenuBackground", at = @At("HEAD"), cancellable = true)
    private void polyplus$keepPanoramaTabBackground(GuiGraphics graphics, int x, int y, int right, int bottom, CallbackInfo ci) {
        if (!MenuPanorama.backdropDrawn()) return;
        graphics.fill(x, y, right, bottom, MenuPanorama.LIST_TINT);
        ci.cancel();
    }
    ^///?}
}
*///?}
