package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 && < 26.1 {
/*import net.minecraft.client.KeyboardHandler;
import org.polyfrost.polyplus.client.gui.screens.settings.KeyBindsPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyBindCaptureChar {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void polyplus$blockCaptureChar(CallbackInfo ci) {
        if (KeyBindsPlatform.blockChar()) ci.cancel();
    }
}
*///?}
