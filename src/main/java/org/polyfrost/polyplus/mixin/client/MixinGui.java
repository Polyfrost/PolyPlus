package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.polyfrost.polyplus.client.gui.MainMenuReplacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

//? if >= 26.2 {
import net.minecraft.client.gui.Gui;
//?}

//? if = 26.2 {
/*import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
*///?}

//? if < 26.3 {
/*import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.social.SocialOverlay;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.polyfrost.polyplus.privacy.PrivacyConsent;
*///?}

//? if < 26.2 && > 1.8.9 {
/*import net.minecraft.client.gui.screens.ShareToLanScreen;
*///?} elif = 1.8.9 {
/*import net.minecraft.client.gui.screen.OpenToLanScreen;
*///?}

//? if >= 26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class MixinGui {
    //? if > 1.8.9 {
    @WrapMethod(method = "setScreen")
    //?} else {
    /*@WrapMethod(method = "openScreen")
    *///?}
    private void polyplus$replaceScreen(Screen screen, Operation<Void> original) {
        // on 26.3+ the LAN controls live inside World Options, so MixinWorldOptionsScreen handles them instead
        //? if = 26.2 {
        /*if (PolyPlusConfig.getReplacePauseLanButton() && PrivacyConsent.allowsOnlineServices() && screen instanceof MultiplayerOptionsScreen) {
            SocialOverlay.INSTANCE.openHostCurrentWorld(ClientPlatform.INSTANCE.currentScreen());
            return;
        }
        *///?} elif < 26.2 && > 1.8.9 {
        /*if (PolyPlusConfig.getReplacePauseLanButton() && PrivacyConsent.allowsOnlineServices() && screen instanceof ShareToLanScreen) {
            SocialOverlay.INSTANCE.openHostCurrentWorld(ClientPlatform.INSTANCE.currentScreen());
            return;
        }
        *///?} elif = 1.8.9 {
        /*if (PolyPlusConfig.getReplacePauseLanButton() && PrivacyConsent.allowsOnlineServices() && screen instanceof OpenToLanScreen) {
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
