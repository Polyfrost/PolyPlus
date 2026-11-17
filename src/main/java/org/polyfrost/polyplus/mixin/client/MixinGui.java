package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.polyfrost.polyplus.client.gui.MainMenuReplacement;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseRouter;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseStage;
import org.polyfrost.polyplus.client.gui.screens.Routed;
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter;
import org.polyfrost.polyplus.client.gui.screens.settings.SettingsMemory;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

//? if >= 26.2 {
import net.minecraft.client.gui.Gui;
//?}

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//?}

//? if = 26.2 {
/*import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
*///?}

//? if < 26.3 {
/*import org.polyfrost.polyplus.client.PolyPlusPauseConfig;
import org.polyfrost.polyplus.client.social.SocialOverlay;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.polyfrost.polyplus.privacy.PrivacyConsent;
*///?}

//? if < 26.2 && > 1.8.9 {
/*import net.minecraft.client.gui.screens.ShareToLanScreen;
*///?} elif = 1.8.9 {
/*import net.minecraft.client.gui.screen.menu.world.OpenToLanScreen;
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
        /*if (PolyPlusPauseConfig.getReplacePauseLanButton() && PrivacyConsent.allowsOnlineServices() && screen instanceof MultiplayerOptionsScreen) {
            SocialOverlay.INSTANCE.openHostCurrentWorld(ClientPlatform.INSTANCE.currentScreen());
            return;
        }
        *///?} elif < 26.2 && > 1.8.9 {
        /*if (PolyPlusPauseConfig.getReplacePauseLanButton() && PrivacyConsent.allowsOnlineServices() && screen instanceof ShareToLanScreen) {
            SocialOverlay.INSTANCE.openHostCurrentWorld(ClientPlatform.INSTANCE.currentScreen());
            return;
        }
        *///?} elif = 1.8.9 {
        /*if (PolyPlusPauseConfig.getReplacePauseLanButton() && PrivacyConsent.allowsOnlineServices() && screen instanceof OpenToLanScreen) {
            SocialOverlay.INSTANCE.openHostCurrentWorld(ClientPlatform.INSTANCE.currentScreen());
            return;
        }
        *///?}
        if (screen == null) screen = SettingsMemory.returning();
        screen = PauseRouter.replace(screen);
        PauseStage.onScreen(screen);
        screen = MainMenuReplacement.replaceLists(screen);
        Routed routed = ScreenRouter.route(screen);
        if (routed.getConsumed()) return;
        screen = routed.getScreen();
        if (MainMenuReplacement.enabled() && polyplus$opensTitleScreen(screen)) {
            if (!MainMenuReplacement.alreadyOpen()) {
                original.call(MainMenuReplacement.create());
            }
            return;
        }
        boolean leavingPause = PauseRouter.leaving(screen);
        original.call(screen);
        if (leavingPause) ((MinecraftAccessor) Minecraft.getInstance()).polyplus$setAttackDelay(0);
    }

    //? if > 1.8.9 {
    @ModifyVariable(method = "setScreen", at = @At("STORE"), argsOnly = true)
    private Screen polyplus$routeDeathScreen(Screen screen) {
        if (screen == null || screen.getClass() != DeathScreen.class) return screen;
        Routed routed = ScreenRouter.route(screen);
        return routed.getConsumed() ? screen : routed.getScreen();
    }
    //?}

    @Unique
    private static boolean polyplus$opensTitleScreen(Screen screen) {
        return screen instanceof TitleScreen || (screen == null && !LobbyScene.hasRealPlayer());
    }
}
