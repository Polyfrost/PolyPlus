package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractSelectionList.class)
public class MixinAbstractSelectionList {
    @WrapMethod(
        //? if >= 26.1 {
        method = "extractListBackground"
        //?} else {
        /*method = "renderListBackground"
        *///?}
    )
    private void polyplus$keepPanoramaListBackground(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        Operation<Void> original
    ) {
        if (MenuPanorama.backdropDrawn()) {
            AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
            graphics.fill(list.getX(), list.getY(), list.getRight(), list.getBottom(), MenuPanorama.LIST_TINT);
            return;
        }
        original.call(graphics);
    }
}
