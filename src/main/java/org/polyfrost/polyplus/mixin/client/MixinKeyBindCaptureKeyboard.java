package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import org.lwjgl.input.Keyboard;
import org.polyfrost.polyplus.client.gui.screens.settings.KeyBindsPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Keyboard.class, remap = false)
public class MixinKeyBindCaptureKeyboard {
    @Inject(method = "next", at = @At("RETURN"), cancellable = true)
    private static void polyplus$captureKey(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && KeyBindsPlatform.consumeKey(Keyboard.getEventKey(), Keyboard.getEventKeyState())) {
            cir.setReturnValue(Keyboard.next());
        }
    }
}
*///?}
