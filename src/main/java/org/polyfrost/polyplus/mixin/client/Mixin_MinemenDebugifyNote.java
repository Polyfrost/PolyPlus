package org.polyfrost.polyplus.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.compat.DebugifyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatListener.class)
public class Mixin_MinemenDebugifyNote {
    @ModifyVariable(method = "handleSystemMessage", at = @At("HEAD"), argsOnly = true)
    private Component polyplus$noteDebugifyIsSafe(Component message) {
        if (!DebugifyCompat.getBannableFixesDisabled()) {
            return message;
        }
        String text = message.getString();
        if (!text.contains("[MMC]") || !text.contains("Debugify")) {
            return message;
        }
        return message.copy()
                .append(Component.literal("\n"))
                .append(Component.translatable("polyplus.mmc.debugifyNote").withStyle(ChatFormatting.GREEN));
    }
}
