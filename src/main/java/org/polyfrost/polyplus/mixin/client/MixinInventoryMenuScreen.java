package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.options.GameOptions;
import org.polyfrost.polyplus.PolyPlusEntrypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InventoryMenuScreen.class)
public abstract class MixinInventoryMenuScreen {
    @ModifyExpressionValue(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/game/inventory/InventoryMenuScreen;isControlDown()Z"))
    private boolean polyplus$dropStackModifier(boolean controlDown) {
        return controlDown || GameOptions.isPressed(PolyPlusEntrypoint.dropModifier);
    }
}
*///?}
