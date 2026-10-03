package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.options.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class MixinOptionsScreen extends Screen {
    @Unique
    private static final int[] polyplus$REMOVED_BUTTONS = {107, 104};

    @Unique
    private static final int[] polyplus$GRID_BUTTONS = {110, 106, 101, 100, 102, 103, 105, 8675309};

    @Unique
    private static final int polyplus$DONE_BUTTON = 200;

    @Unique
    private static final int polyplus$HEADER = 61;

    @Unique
    private static final int polyplus$FOOTER = 33;

    @Unique
    private static final int polyplus$TITLE_Y = (polyplus$HEADER - (9 + 8 + 20)) / 2;

    @Unique
    private static final int polyplus$TOP_ROW_Y = polyplus$TITLE_Y + 9 + 8;

    @ModifyConstant(method = "render", constant = @Constant(intValue = 15))
    private int polyplus$modernTitleY(int y) {
        return polyplus$TITLE_Y;
    }

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

        for (ButtonWidget button : this.buttons) {
            if (button.y == this.height / 6 - 12) {
                button.x += button.x < this.width / 2 ? 1 : -1;
                button.y = polyplus$TOP_ROW_Y;
            }
        }

        int count = 0;
        for (ButtonWidget button : this.buttons) {
            for (int id : polyplus$GRID_BUTTONS) {
                if (button.id == id) count++;
            }
        }
        int gridTop = Math.min(polyplus$HEADER + 30, this.height - polyplus$FOOTER - 24 * ((count + 1) / 2));

        int slot = 0;
        for (int id : polyplus$GRID_BUTTONS) {
            for (ButtonWidget button : this.buttons) {
                if (button.id != id) continue;
                button.x = this.width / 2 - 154 + slot % 2 * 158;
                button.y = gridTop + 24 * (slot / 2);
                slot++;
                break;
            }
        }

        for (ButtonWidget button : this.buttons) {
            if (button.id == polyplus$DONE_BUTTON) {
                button.y = this.height - polyplus$FOOTER + (polyplus$FOOTER - 20) / 2;
                break;
            }
        }
    }
}
*///?}
