package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.MouseHandler;
import org.polyfrost.polyplus.client.gui.PauseMenuPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonInfo;
//?}

/**
 * Tracks the mouse buttons while a screen is open, which MouseHandler.isLeftPressed doesn't, for dragging the
 * vanilla pause menu's player preview.
 */
@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    //? if >= 1.21.9 {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void polyplus$trackButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        PauseMenuPanel.onMouseButton(button.button(), action);
    }
    //?} else {
    /*@Inject(method = "onPress", at = @At("HEAD"))
    private void polyplus$trackButton(long window, int button, int action, int mods, CallbackInfo ci) {
        PauseMenuPanel.onMouseButton(button, action);
    }
    *///?}
}
//?}
