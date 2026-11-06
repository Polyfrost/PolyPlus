package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
//? if >= 1.21.11
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {
    //? if >= 1.21.11 {
    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
    private void polyplus$hideInLobby(Frustum frustum, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }
    //?} elif >= 1.21.10 {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void polyplus$hideInLobby(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }
    *///?} else {
    /*@Inject(method = { "render", "renderAfterTranslucents" }, at = @At("HEAD"), cancellable = true)
    private void polyplus$hideInLobby(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }
    *///?}
}
//?}
