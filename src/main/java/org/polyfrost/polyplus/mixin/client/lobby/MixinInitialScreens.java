package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.Overlay;
import org.polyfrost.polyplus.client.gui.lobbyshell.StartupAccessibility;
import org.polyfrost.polyplus.client.lobby.StartupOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.2 {
import net.minecraft.client.gui.Gui;
//?} else {
/*import net.minecraft.client.Minecraft;
*///?}

//? if >= 26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class MixinInitialScreens {
    @ModifyExpressionValue(method = "addInitialScreens", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;onboardAccessibility:Z"))
    private boolean polyplus$keepAccessibilityOnCard(boolean pending) {
        return pending && !StartupAccessibility.replacesVanilla();
    }

    @Inject(method = "setOverlay", at = @At("HEAD"), cancellable = true)
    private void polyplus$keepStartupOverlay(Overlay overlay, CallbackInfo ci) {
        if (overlay == null && StartupOverlay.holdsOverlay()) ci.cancel();
    }
}
//?}
