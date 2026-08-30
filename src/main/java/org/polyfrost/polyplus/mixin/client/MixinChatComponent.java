package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.polyfrost.polyplus.client.emoji.EmojiRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class MixinChatComponent {
    @ModifyVariable(
        //? if >= 26.1 {
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        //?} else {
        /*method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        *///?}
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Component polyplus$emojiMessage(Component original) {
        return EmojiRegistry.transformForViewer(original);
    }

    @WrapMethod(
        //? if >= 26.1 {
        method = "addClientSystemMessage"
        //?} else {
        /*method = "addMessage(Lnet/minecraft/network/chat/Component;)V"
        *///?}
    )
    private void polyplus$hideBobbyUpgradeMessage(Component message, Operation<Void> original) {
        if (polyplus$isBobbyUpgradeMessage(message) && polyplus$onHypixel()) {
            return;
        }
        original.call(message);
    }

    @Unique
    private static boolean polyplus$isBobbyUpgradeMessage(Component message) {
        if (!(message.getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        String key = contents.getKey();
        return "bobby.upgrade.required".equals(key) || "bobby.upgrade.fallback_world".equals(key);
    }

    @Unique
    private static boolean polyplus$onHypixel() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null || server.ip == null) {
            return false;
        }
        String host = server.ip.toLowerCase(Locale.ROOT);
        int portSeparator = host.indexOf(':');
        if (portSeparator >= 0) {
            host = host.substring(0, portSeparator);
        }
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
}
