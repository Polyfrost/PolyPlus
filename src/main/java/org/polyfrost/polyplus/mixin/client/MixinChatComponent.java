package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.polyfrost.polyplus.client.emoji.EmojiRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.Locale;

//? if < 1.21.11 {
/*import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?}

@Mixin(ChatComponent.class)
public class MixinChatComponent {
    //? if < 1.21.11 {
    /*// emoji are applied to the display lines only, so the stored message stays comparable for chat compacting mods
    @ModifyArg(
        method = "addMessageToDisplayQueue",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
        index = 0
    )
    private FormattedText emojiLines(FormattedText text) {
        return EmojiRegistry.transformForViewer(text);
    }
    *///?}

    @WrapMethod(
        //? if >= 26.1 {
        method = "addClientSystemMessage"
        //?} else {
        /*method = "addMessage(Lnet/minecraft/network/chat/Component;)V"
        *///?}
    )
    private void hideBobbyUpgradeMessage(Component message, Operation<Void> original) {
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
