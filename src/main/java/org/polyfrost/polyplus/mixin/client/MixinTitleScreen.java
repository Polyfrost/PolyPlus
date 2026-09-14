package org.polyfrost.polyplus.mixin.client;

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
