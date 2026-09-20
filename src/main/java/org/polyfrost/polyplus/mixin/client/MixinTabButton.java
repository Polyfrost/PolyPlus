package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.gui.components.TabButton;
import org.spongepowered.asm.mixin.Mixin;

//? if = 26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}

//? if < 26.2 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
*///?}

//? if < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

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
//?}
