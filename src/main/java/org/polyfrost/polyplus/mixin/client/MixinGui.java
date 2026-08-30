package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
//? if < 26.2 {
/*import net.minecraft.client.gui.screens.ShareToLanScreen;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.social.SocialOverlay;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
*///?}
import org.polyfrost.polyplus.client.gui.MainMenuReplacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

//? if >= 26.2 {
@Mixin(net.minecraft.client.gui.Gui.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class MixinGui {
    @WrapMethod(method = "setScreen")
    private void polyplus$replaceScreen(Screen screen, Operation<Void> original) {
        //? if < 26.2 {
        /*if (PolyPlusConfig.getReplacePauseLanButton() && screen instanceof ShareToLanScreen) {
            SocialOverlay.INSTANCE.openHostCurrentWorld(ClientPlatform.INSTANCE.currentScreen());
            return;
        }
        *///?}
        if (MainMenuReplacement.enabled() && polyplus$opensTitleScreen(screen)) {
            if (!MainMenuReplacement.alreadyOpen()) {
                original.call(MainMenuReplacement.create());
            }
            return;
        }
        original.call(screen);
    }

    @Unique
    private static boolean polyplus$opensTitleScreen(Screen screen) {
        return screen instanceof TitleScreen || (screen == null && Minecraft.getInstance().player == null);
    }
}
