package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.TabButton;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TabButton.class)
public class MixinTabButton {
    //? if < 26.2 {
    /*@WrapMethod(
        //? if >= 26.1 {
        method = "extractMenuBackground"
        //?} else {
        /^method = "renderMenuBackground"
        ^///?}
    )
    private void polyplus$keepPanoramaTabBackground(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /^GuiGraphics graphics,
        ^///?}
        int x, int y, int right, int bottom, Operation<Void> original
    ) {
        if (MenuPanorama.backdropDrawn()) {
            graphics.fill(x, y, right, bottom, MenuPanorama.LIST_TINT);
            return;
        }
        original.call(graphics, x, y, right, bottom);
    }
    *///?}
}
