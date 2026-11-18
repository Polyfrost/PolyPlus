package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.options.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.polyfrost.polyplus.client.gui.VanillaMenuButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class MixinOptionsScreenButton extends Screen {
    @Inject(method = "init", at = @At("RETURN"))
    private void polyplus$addOneClientButton(CallbackInfo ci) {
        this.buttons.add(VanillaMenuButton.legacyOptionsButton(this.width));
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$openOneClientSettings(ButtonWidget button, CallbackInfo ci) {
        if (VanillaMenuButton.onLegacyOptionsButton(button.id)) ci.cancel();
    }
}
*///?}
