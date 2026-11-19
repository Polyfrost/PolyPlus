package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.options.GameOptions;
import org.polyfrost.polyplus.PolyPlusEntrypoint;
import org.polyfrost.polyplus.client.gui.ClientWatermark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenuScreen.class)
public abstract class MixinInventoryMenuScreen {
    @ModifyExpressionValue(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/game/inventory/InventoryMenuScreen;isControlDown()Z"))
    private boolean polyplus$dropStackModifier(boolean controlDown) {
        return controlDown || GameOptions.isPressed(PolyPlusEntrypoint.dropModifier);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$pressWatermark(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button == 0 && ClientWatermark.onPress()) ci.cancel();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void polyplus$releaseWatermark(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button == 0 && ClientWatermark.onRelease()) ci.cancel();
    }
}
*///?}
