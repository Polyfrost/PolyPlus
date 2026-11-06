package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import org.polyfrost.polyplus.client.lobby.LobbyCapture;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.lobby.StartupOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyplus$noteQuickPlay(GameConfig config, CallbackInfo ci) {
        StartupOverlay.noteQuickPlay(config.quickPlay.isEnabled());
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void polyplus$stepLobbyClock(boolean advanceGameTime, CallbackInfo ci) {
        LobbyScene.onFrameStart();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void polyplus$tickLobby(CallbackInfo ci) {
        LobbyScene.onClientTick();
        LobbyCapture.onClientTick();
    }

    //? if >= 1.21.10 {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void polyplus$releaseLobby(ClientLevel level, CallbackInfo ci) {
        LobbyScene.stop();
    }
    //?} else {
    /*@Inject(method = "setLevel", at = @At("HEAD"))
    private void polyplus$releaseLobby(CallbackInfo ci) {
        LobbyScene.stop();
    }
    *///?}

    //? if >= 1.21.11 {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void polyplus$captureOnLeave(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        if (((Minecraft) (Object) this).isRunning()) LobbyCapture.onLeave();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
    private void polyplus$landLeaveCapture(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        LobbyCapture.drain();
    }
    //?} else {
    /*@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void polyplus$captureOnLeave(Screen screen, boolean keepResourcePacks, CallbackInfo ci) {
        if (((Minecraft) (Object) this).isRunning()) LobbyCapture.onLeave();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("RETURN"))
    private void polyplus$landLeaveCapture(Screen screen, boolean keepResourcePacks, CallbackInfo ci) {
        LobbyCapture.drain();
    }
    *///?}

    //? if >= 1.21.11 {
    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("HEAD"))
    private void polyplus$releaseLobbyEngines(ClientLevel level, boolean stopSound, CallbackInfo ci) {
        if (level == null) LobbyScene.stop();
    }
    //?} else {
    /*@Inject(method = "updateLevelInEngines", at = @At("HEAD"))
    private void polyplus$releaseLobbyEngines(ClientLevel level, CallbackInfo ci) {
        if (level == null) LobbyScene.stop();
    }
    *///?}

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void polyplus$noLobbyPause(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }
}
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.polyfrost.polyplus.client.lobby.LobbyCapture;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Shadow
    volatile boolean running;

    @Inject(method = "runGame", at = @At("HEAD"))
    private void polyplus$stepLobbyClock(CallbackInfo ci) {
        LobbyScene.onFrameStart();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void polyplus$tickLobby(CallbackInfo ci) {
        LobbyScene.onClientTick();
        LobbyCapture.onClientTick();
    }

    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
    private void polyplus$releaseLobby(ClientWorld world, String message, CallbackInfo ci) {
        LobbyScene.stop();
    }

    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
    private void polyplus$captureOnLeave(ClientWorld world, String message, CallbackInfo ci) {
        if (world == null && running) LobbyCapture.onLeave();
    }

    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("RETURN"))
    private void polyplus$landLeaveCapture(ClientWorld world, String message, CallbackInfo ci) {
        if (world == null) LobbyCapture.drain();
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void polyplus$noLobbyPause(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }
}
*///?}
