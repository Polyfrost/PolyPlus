package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.polyfrost.polyplus.client.PolyPlusRecentServers;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import org.polyfrost.polyplus.client.gui.screens.connection.ConnectingScreen;
import org.polyfrost.polyplus.client.gui.screens.connection.ConnectionTarget;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.launcher.SessionRefresh;
import org.polyfrost.polyplus.client.network.p2p.P2PConnectionContext;
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class MixinConnectScreen {
    @Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
    private static void polyplus$blockWhileConnecting(
        Screen screen,
        Minecraft minecraft,
        ServerAddress address,
        ServerData serverData,
        boolean quickPlay,
        TransferState transferState,
        CallbackInfo ci
    ) {
        if (ClientPlatform.INSTANCE.currentScreen() instanceof ConnectingScreen) {
            ci.cancel();
            return;
        }
        ConnectionTarget.note(serverData, address);
    }

    //? if >= 1.21.11 {
    @WrapWithCondition(
        method = "startConnecting",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;disconnectWithProgressScreen(Z)V")
    )
    private static boolean polyplus$keepLobbyScene(Minecraft minecraft, boolean stopSound) {
        return !LobbyScene.holdForJoin();
    }
    //?} elif >= 1.21.8 {
    /*@WrapWithCondition(
        method = "startConnecting",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;disconnectWithProgressScreen()V")
    )
    private static boolean polyplus$keepLobbyScene(Minecraft minecraft) {
        return !LobbyScene.holdForJoin();
    }
    *///?} else {
    /*@WrapWithCondition(
        method = "startConnecting",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;disconnect()V")
    )
    private static boolean polyplus$keepLobbyScene(Minecraft minecraft) {
        return !LobbyScene.holdForJoin();
    }
    *///?}

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void polyplus$trackConnectAttempt(
        Screen screen,
        Minecraft minecraft,
        ServerAddress address,
        ServerData serverData,
        boolean quickPlay,
        TransferState transferState,
        CallbackInfo ci
    ) {
        SessionRefresh.onConnectStarted(screen, address, serverData, quickPlay, transferState);
        if (serverData == null || !P2PSessionManager.P2P_PLACEHOLDER_IP.equals(serverData.ip)) {
            P2PConnectionContext.clearPendingJoin();
            if (serverData != null) {
                PolyPlusRecentServers.record(serverData.name, serverData.ip);
            }
        }
    }
}
//?} else {
/*import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.world.ClientWorld;
import org.polyfrost.polyplus.client.PolyPlusRecentServers;
import org.polyfrost.polyplus.client.gui.screens.connection.ConnectionTarget;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.launcher.SessionRefresh;
import org.polyfrost.polyplus.client.network.p2p.P2PConnectionContext;
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class MixinConnectScreen {
    @WrapWithCondition(
        method = {
            "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/options/ServerListEntry;)V",
            "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/Minecraft;Ljava/lang/String;I)V"
        },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setWorld(Lnet/minecraft/client/world/ClientWorld;)V")
    )
    private boolean polyplus$keepLobbyScene(Minecraft minecraft, ClientWorld world) {
        return !LobbyScene.holdForJoin();
    }

    @Inject(
        method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/options/ServerListEntry;)V",
        at = @At("RETURN")
    )
    private void polyplus$noteServer(Screen parent, Minecraft minecraft, ServerListEntry server, CallbackInfo ci) {
        ConnectionTarget.note(server, null, 0);
    }

    @Inject(
        method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/Minecraft;Ljava/lang/String;I)V",
        at = @At("RETURN")
    )
    private void polyplus$noteAddress(Screen parent, Minecraft minecraft, String host, int port, CallbackInfo ci) {
        ConnectionTarget.note(null, host, port);
    }

    @Inject(
        method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/options/ServerListEntry;)V",
        at = @At("RETURN")
    )
    private void polyplus$trackConnectAttempt(Screen parent, Minecraft minecraft, ServerListEntry server, CallbackInfo ci) {
        SessionRefresh.onConnectStarted(parent, server);
        if (server != null && !P2PSessionManager.P2P_PLACEHOLDER_IP.equals(server.ip)) {
            PolyPlusRecentServers.record(server.name, server.ip);
        }
    }

    @Inject(method = "connect", at = @At("HEAD"))
    private void polyplus$clearStaleP2PJoin(String address, int port, CallbackInfo ci) {
        if (!P2PSessionManager.P2P_PLACEHOLDER_IP.equals(address)) {
            P2PConnectionContext.clearPendingJoin();
        }
    }
}
*///?}
