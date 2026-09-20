package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.screens.TitleScreen;
import org.polyfrost.polyplus.client.gui.MainMenuReplacement;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    @WrapMethod(method = "init")
    private void polyplus$replaceMainMenu(Operation<Void> original) {
        if (MainMenuReplacement.enabled() && !MainMenuReplacement.alreadyOpen()) {
            MainMenuReplacement.open();
            return;
        }
        original.call();
    }
}
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.polyfrost.polyplus.client.gui.MainMenuReplacement;
import org.polyfrost.polyplus.client.gui.VanillaMenuButton;
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    @WrapMethod(method = "init")
    private void polyplus$replaceMainMenu(Operation<Void> original) {
        original.call();
        if (Minecraft.getInstance().screen != (Object) this) return;
        if (MainMenuReplacement.enabled() && !MainMenuReplacement.alreadyOpen()) {
            MainMenuReplacement.open();
            return;
        }
        ButtonWidget button = VanillaMenuButton.legacyButton(((Screen) (Object) this).width);
        if (button != null) ((ScreenAccessor) this).polyplus$buttons().add(button);
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$openPolyPlusMenu(ButtonWidget button, CallbackInfo ci) {
        if (button.id != VanillaMenuButton.LEGACY_BUTTON_ID) return;
        VanillaMenuButton.onLegacyButton();
        ci.cancel();
    }
}
*///?}
