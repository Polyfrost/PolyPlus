package org.polyfrost.polyplus.mixin.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class Mixin_HideBobbyUpgradeMessage {
    //? if >= 26.1 {
    @Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    *///?}
    private void polyplus$hideBobbyUpgradeMessage(Component message, CallbackInfo ci) {
        if (!(message.getContents() instanceof TranslatableContents contents)) {
            return;
        }
        String key = contents.getKey();
        if (!"bobby.upgrade.required".equals(key) && !"bobby.upgrade.fallback_world".equals(key)) {
            return;
        }
        if (polyplus$onHypixel()) {
            ci.cancel();
        }
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
