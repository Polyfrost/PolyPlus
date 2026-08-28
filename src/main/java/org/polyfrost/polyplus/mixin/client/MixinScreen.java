package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
public class MixinScreen {
    @Inject(
        //? if >= 26.1 {
        method = "extractBackground",
        //?} else {
        /*method = "renderBackground",
        *///?}
        at = @At("HEAD")
    )
    private void polyplus$beginBackgroundPass(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int mouseX, int mouseY, float tickDelta, CallbackInfo ci
    ) {
        MenuPanorama.beginPass();
    }

    @Inject(
        //? if >= 26.1 {
        method = "extractPanorama",
        //?} else {
        /*method = "renderPanorama",
        *///?}
        at = @At("TAIL")
    )
    private void polyplus$drawBackdrop(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        float tickDelta, CallbackInfo ci
    ) {
        if (MenuPanorama.panoramaBackdrop()) return;
        MenuPanorama.drawBackdrop(graphics, (Screen) (Object) this, true);
    }

    @WrapMethod(
        //? if >= 26.1 {
        method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"
        //?} else {
        /*method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V"
        *///?}
    )
    private void polyplus$replaceMenuBackground(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int x, int y, int width, int height, Operation<Void> original
    ) {
        Screen self = (Screen) (Object) this;
        if (MenuPanorama.active(self) && MenuPanorama.drawBackdrop(graphics, self, false)) return;
        original.call(graphics, x, y, width, height);
    }
}
