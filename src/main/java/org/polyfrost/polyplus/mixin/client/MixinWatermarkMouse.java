package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import org.polyfrost.polyplus.client.gui.ClientWatermark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonInfo;
//?}

@Mixin(MouseHandler.class)
public class MixinWatermarkMouse {
    //? if >= 1.21.9 {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void polyplus$dragWatermark(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (button.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        if (action == 1 ? ClientWatermark.onPress() : action == 0 && ClientWatermark.onRelease()) ci.cancel();
    }
    //?} else {
    /*@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void polyplus$dragWatermark(long window, int button, int action, int mods, CallbackInfo ci) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return;
        if (action == 1 ? ClientWatermark.onPress() : action == 0 && ClientWatermark.onRelease()) ci.cancel();
    }
    *///?}
}
//?}
