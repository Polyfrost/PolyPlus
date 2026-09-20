package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {

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
//?} else {
/*import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import org.polyfrost.polyplus.client.emoji.EmojiRegistry;
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPacketListener {
    @ModifyVariable(method = "sendPacket", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet polyplus$emojiChat(Packet packet) {
        if (packet instanceof ChatMessageC2SPacket chat) {
            String message = chat.getMessage();
            String converted = EmojiRegistry.toShortcodes(message);
            if (!converted.equals(message)) {
                return new ChatMessageC2SPacket(converted);
            }
        }
        return packet;
    }

    @Inject(method = "closeWorld", at = @At("HEAD"))
    private void polyplus$onDisconnect(CallbackInfo ci) {
        P2PSessionManager.onClientDisconnected();
    }
}
*///?}
