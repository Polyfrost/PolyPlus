package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PauseScreen.class)
public class MixinPauseScreen {
    @ModifyExpressionValue(
        method = "createPauseMenu",
        at = @At(
            value = "FIELD",
            //? if >= 26.2 {
            target = "Lnet/minecraft/client/gui/screens/PauseScreen;MULTIPLAYER_OPTIONS:Lnet/minecraft/network/chat/Component;",
            //?} else {
            /*target = "Lnet/minecraft/client/gui/screens/PauseScreen;SHARE_TO_LAN:Lnet/minecraft/network/chat/Component;",
            *///?}
            opcode = Opcodes.GETSTATIC
        )
    )
    private Component polyplus$hostWorldLabel(Component original) {
        return PolyPlusConfig.getReplacePauseLanButton() ? Component.translatable("polyplus.hostWorld") : original;
    }
}
