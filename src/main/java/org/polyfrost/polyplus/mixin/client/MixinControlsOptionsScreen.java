package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.options.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.GameOptions;
import org.polyfrost.polyplus.client.features.SensitivityConverter;
import org.polyfrost.polyplus.client.gui.SensitivityConverterScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ControlsOptionsScreen.class)
public abstract class MixinControlsOptionsScreen extends Screen {
    @Unique
    private static final int polyplus$CONVERT_BUTTON = 8675310;

    @Shadow
    private GameOptions options;

    @Inject(method = "init", at = @At("RETURN"))
    private void polyplus$addConvertButton(CallbackInfo ci) {
        if (!SensitivityConverter.INSTANCE.isWindows()) return;
        this.buttons.add(new ButtonWidget(polyplus$CONVERT_BUTTON, this.width / 2 + 5, 18 + 24, 150, 20, "Convert Sensitivity..."));
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$openConverter(ButtonWidget button, CallbackInfo ci) {
        if (button.id != polyplus$CONVERT_BUTTON) return;
        ci.cancel();

        this.minecraft.openScreen(new SensitivityConverterScreen(this, this.options));
    }
}
*///?}
