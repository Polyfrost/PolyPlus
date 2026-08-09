package org.polyfrost.polyplus.mixin.client;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class Mixin_MenuPanoramaBackground {
    //? if >= 26.1 {
    @Inject(method = "extractBackground", at = @At("HEAD"))
    private void polyplus$beginBackgroundPass(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        MenuPanorama.beginPass();
    }

    @Inject(method = "extractPanorama", at = @At("TAIL"))
    private void polyplus$drawBackdrop(GuiGraphicsExtractor graphics, float tickDelta, CallbackInfo ci) {
        if (MenuPanorama.panoramaBackdrop()) return;
        MenuPanorama.drawBackdrop(graphics, (Screen) (Object) this, true);
    }

    @Inject(
        method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void polyplus$replaceMenuBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (!MenuPanorama.active((Screen) (Object) this)) return;
        if (MenuPanorama.drawBackdrop(graphics, (Screen) (Object) this, false)) ci.cancel();
    }
    //?} else {
    /*@Inject(method = "renderBackground", at = @At("HEAD"))
    private void polyplus$beginBackgroundPass(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        MenuPanorama.beginPass();
    }

    @Inject(method = "renderPanorama", at = @At("TAIL"))
    private void polyplus$drawBackdrop(GuiGraphics graphics, float tickDelta, CallbackInfo ci) {
        if (MenuPanorama.panoramaBackdrop()) return;
        MenuPanorama.drawBackdrop(graphics, (Screen) (Object) this, true);
    }

    @Inject(
        method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void polyplus$replaceMenuBackground(GuiGraphics graphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (!MenuPanorama.active((Screen) (Object) this)) return;
        if (MenuPanorama.drawBackdrop(graphics, (Screen) (Object) this, false)) ci.cancel();
    }
    *///?}
}
