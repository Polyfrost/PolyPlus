package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.polyfrost.polyplus.client.emoji.EmojiRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    @ModifyVariable(method = "sendChat(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String polyplus$emojiChat(String message) {
        return EmojiRegistry.toShortcodes(message);
    }

    @ModifyVariable(method = "sendCommand(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String polyplus$emojiCommand(String command) {
        return EmojiRegistry.toShortcodes(command);
    }
}
