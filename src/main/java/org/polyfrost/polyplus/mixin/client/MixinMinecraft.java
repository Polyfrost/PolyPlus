package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.options.GameOptions;
import org.polyfrost.polyplus.PolyPlusEntrypoint;
*///?}

@Mixin(value = Minecraft.class, priority = 1500)
public class MixinMinecraft {
    //? if > 1.8.9 {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void polyplus$beginMenuBackdropFrame(boolean renderLevel, CallbackInfo ci) {
        MenuPanorama.beginPass();
    }
    //?} else {
    /*@Inject(method = "runGame", at = @At("HEAD"))
    private void polyplus$beginMenuBackdropFrame(CallbackInfo ci) {
        MenuPanorama.beginPass();
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "CONSTANT", args = "intValue=59"))
    private int polyplus$hideScreenKey(int key) {
        return PolyPlusEntrypoint.remap(PolyPlusEntrypoint.hideScreen, key);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "CONSTANT", args = "intValue=61"))
    private int polyplus$customDebugKey(int key) {
        return PolyPlusEntrypoint.remap(PolyPlusEntrypoint.customDebug, key);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "CONSTANT", args = "intValue=62"))
    private int polyplus$clearShadersKey(int key) {
        return PolyPlusEntrypoint.remap(PolyPlusEntrypoint.clearShaders, key);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;isControlDown()Z"))
    private boolean polyplus$dropStackModifier(boolean controlDown) {
        return controlDown || GameOptions.isPressed(PolyPlusEntrypoint.dropModifier);
    }
    *///?}
}
