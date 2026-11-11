package org.polyfrost.polyplus.mixin.client.lobby;

import net.minecraft.server.MinecraftServer;
import org.polyfrost.polyplus.client.lobby.LobbyCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if > 1.8.9 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {
    //? if > 1.8.9 {
    @Inject(method = "saveEverything(ZZZ)Z", at = @At("RETURN"))
    private void polyplus$captureAfterSave(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        LobbyCapture.onWorldSaved();
    }
    //?} else {
    /*@Inject(method = "saveWorlds(Z)V", at = @At("RETURN"))
    private void polyplus$captureAfterSave(boolean silent, CallbackInfo ci) {
        LobbyCapture.onWorldSaved();
    }
    *///?}
}
