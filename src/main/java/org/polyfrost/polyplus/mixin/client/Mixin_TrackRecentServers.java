package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.polyfrost.polyplus.client.PolyPlusRecentServers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class Mixin_TrackRecentServers {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void polyplus$trackRecentServer(
        Screen screen,
        Minecraft minecraft,
        ServerAddress address,
        ServerData serverData,
        boolean quickPlay,
        TransferState transferState,
        CallbackInfo ci
    ) {
        if (serverData != null) {
            PolyPlusRecentServers.record(serverData.name, serverData.ip);
        }
    }
}
