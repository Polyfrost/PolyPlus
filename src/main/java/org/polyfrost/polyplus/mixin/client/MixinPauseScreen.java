package org.polyfrost.polyplus.mixin.client;

//? if < 26.3 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.privacy.PrivacyConsent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
*///?}

/**
 * Renames the pause menu's dedicated LAN button.
 * On 26.3 this is inside the World Options screen (see MixinWorldOptionsScreen).
 */
//? if < 26.3 {
/*@Mixin(PauseScreen.class)
*///?}
public class MixinPauseScreen {
    //? if < 26.3 {
    /*@ModifyExpressionValue(
        method = "createPauseMenu",
        at = @At(
            value = "FIELD",
            //? if >= 26.2 {
            target = "Lnet/minecraft/client/gui/screens/PauseScreen;MULTIPLAYER_OPTIONS:Lnet/minecraft/network/chat/Component;",
            //?} else {
            /^target = "Lnet/minecraft/client/gui/screens/PauseScreen;SHARE_TO_LAN:Lnet/minecraft/network/chat/Component;",
            ^///?}
            opcode = Opcodes.GETSTATIC
        )
    )
    private Component polyplus$hostWorldLabel(Component original) {
        return PolyPlusConfig.getReplacePauseLanButton() && PrivacyConsent.allowsOnlineServices()
            ? Component.translatable("polyplus.hostWorld")
            : original;
    }
    *///?}
}
