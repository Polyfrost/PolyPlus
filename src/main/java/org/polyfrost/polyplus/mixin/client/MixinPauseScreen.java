package org.polyfrost.polyplus.mixin.client;

//? if < 26.3 && > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.privacy.PrivacyConsent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
*///?} elif = 1.8.9 {
/*import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor;
import org.polyfrost.polyplus.privacy.PrivacyConsent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Renames the pause menu's dedicated LAN button.
 * On 26.3 this is inside the World Options screen (see MixinWorldOptionsScreen).
 */
//? if < 26.3 && > 1.8.9 {
/*@Mixin(PauseScreen.class)
*///?} elif = 1.8.9 {
/*@Mixin(GameMenuScreen.class)
*///?}
public class MixinPauseScreen {
    //? if < 26.3 && > 1.8.9 {
    /*@ModifyExpressionValue(
        method = "createPauseMenu",
        at = @At(
            value = "FIELD",
            //? if >= 26.2 {
            /^target = "Lnet/minecraft/client/gui/screens/PauseScreen;MULTIPLAYER_OPTIONS:Lnet/minecraft/network/chat/Component;",
            ^///?} else {
            target = "Lnet/minecraft/client/gui/screens/PauseScreen;SHARE_TO_LAN:Lnet/minecraft/network/chat/Component;",
            //?}
            opcode = Opcodes.GETSTATIC
        )
    )
    private Component polyplus$hostWorldLabel(Component original) {
        return PolyPlusConfig.getReplacePauseLanButton() && PrivacyConsent.allowsOnlineServices()
            ? Component.translatable("polyplus.hostWorld")
            : original;
    }
    *///?} elif = 1.8.9 {
    /*@Inject(method = "init", at = @At("TAIL"))
    private void polyplus$hostWorldLabel(CallbackInfo ci) {
        if (!PolyPlusConfig.getReplacePauseLanButton() || !PrivacyConsent.allowsOnlineServices()) return;
        for (ButtonWidget button : ((ScreenAccessor) this).polyplus$buttons()) {
            if (button.id == 7) button.message = I18n.translate("polyplus.hostWorld");
        }
    }
    *///?}
}
