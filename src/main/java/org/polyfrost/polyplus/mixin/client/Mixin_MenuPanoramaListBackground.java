package org.polyfrost.polyplus.mixin.client;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public class Mixin_MenuPanoramaListBackground {
    //? if >= 26.1 {
    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    private void polyplus$keepPanoramaListBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!MenuPanorama.backdropDrawn()) return;
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        graphics.fill(list.getX(), list.getY(), list.getRight(), list.getBottom(), MenuPanorama.LIST_TINT);
        ci.cancel();
    }
    //?} else {
    /*@Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true)
    private void polyplus$keepPanoramaListBackground(GuiGraphics graphics, CallbackInfo ci) {
        if (!MenuPanorama.backdropDrawn()) return;
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        graphics.fill(list.getX(), list.getY(), list.getRight(), list.getBottom(), MenuPanorama.LIST_TINT);
        ci.cancel();
    }
    *///?}
}
