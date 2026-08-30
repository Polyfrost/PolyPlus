package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.client.host.E4mcSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PauseScreen.class)
public class MixinPauseScreen {
    //? if < 26.2 {
    /*@ModifyExpressionValue(
        method = "createPauseMenu",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/PauseScreen;SHARE_TO_LAN:Lnet/minecraft/network/chat/Component;",
            opcode = Opcodes.GETSTATIC
        )
    )
    private Component polyplus$hostWorldLabel(Component original) {
        return E4mcSupport.INSTANCE.isPresent() ? Component.translatable("polyplus.hostWorld") : original;
    }
    *///?}
}
