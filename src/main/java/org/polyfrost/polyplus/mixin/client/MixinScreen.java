package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}

//? if < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

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
        Screen self = (Screen) (Object) this;
        if (!MenuPanorama.panoramaPassNeedsBackdrop(self)) return;
        MenuPanorama.drawBackdrop(graphics, self, true);
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
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screen.inventory.menu.InventoryMenuScreen;
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig;
import org.polyfrost.polyplus.client.gui.LegacyMenuBlur;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreen {
    @Inject(method = "renderBackground(I)V", at = @At("HEAD"))
    private void polyplus$beginBackgroundPass(int offset, CallbackInfo ci) {
        MenuPanorama.beginPass();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void polyplus$endBackgroundPass(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        MenuPanorama.beginPass();
    }

    @WrapOperation(
        method = "renderBackground(I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;fillGradient(IIIIII)V")
    )
    private void polyplus$modernInGameBackground(Screen self, int x1, int y1, int x2, int y2, int top, int bottom, Operation<Void> original) {
        if (PolyPlusMainMenuConfig.getModernInGameMenus() && !(self instanceof InventoryMenuScreen)) {
            LegacyMenuBlur.blur();
            GuiElement.fill(x1, y1, x2, y2, 0x40000000);
            return;
        }
        original.call(self, x1, y1, x2, y2, top, bottom);
    }

    @Inject(method = "drawBackgroundTexture", at = @At("HEAD"), cancellable = true)
    private void polyplus$replaceMenuBackground(int offset, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (MenuPanorama.active(self) && MenuPanorama.drawBackdrop(self, false)) ci.cancel();
    }
}
*///?}
