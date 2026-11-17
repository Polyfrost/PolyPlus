package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import org.lwjgl.input.Mouse;
import org.polyfrost.polyplus.client.gui.screens.settings.KeyBindsPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Mouse.class, remap = false)
public class MixinKeyBindCaptureMouse {
    @Inject(method = "next", at = @At("RETURN"), cancellable = true)
    private static void polyplus$captureButton(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && KeyBindsPlatform.consumeMouse(Mouse.getEventButton(), Mouse.getEventButtonState())) {
            cir.setReturnValue(Mouse.next());
        }
    }
}
*///?}
