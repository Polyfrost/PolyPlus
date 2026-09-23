package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.options.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class MixinOptionsScreen extends Screen {
    @Unique
    private static final int[] polyplus$REMOVED_BUTTONS = {107, 104};

    @Unique
    private static final int[] polyplus$GRID_BUTTONS = {110, 8675309, 106, 102, 101, 100, 105, 103};

    @Unique
    private static final int polyplus$DONE_BUTTON = 200;

    @Inject(method = "init", at = @At("RETURN"))
    private void polyplus$removeDeadSettings(CallbackInfo ci) {
        for (int id : polyplus$REMOVED_BUTTONS) {
            for (int i = 0; i < this.buttons.size(); i++) {
                if (this.buttons.get(i).id == id) {
                    this.buttons.remove(i);
                    break;
                }
            }
        }

        int slot = 0;
        for (int id : polyplus$GRID_BUTTONS) {
            for (ButtonWidget button : this.buttons) {
                if (button.id != id) continue;
                button.x = this.width / 2 - 155 + slot % 2 * 160;
                button.y = this.height / 6 + 42 + 24 * (slot / 2);
                slot++;
                break;
            }
        }

        int rows = (slot + 1) / 2;
        for (ButtonWidget button : this.buttons) {
            if (button.id == polyplus$DONE_BUTTON) {
                button.y = this.height / 6 + 42 + 24 * (rows - 1) + 30;
                break;
            }
        }
    }
}
*///?}
