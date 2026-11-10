package org.polyfrost.polyplus.mixin.compat.renderscale;

//? if >= 1.21.4 {
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.zelo.renderscale.RenderScale", remap = false)
public abstract class MixinRenderScaleLobby {
    @Unique private boolean polyplus$lobbyNative;
    @Unique private boolean polyplus$targetReady;
    @Unique private boolean polyplus$skippedScale;

    @Shadow public abstract void resizeRenderTarget();

    @Inject(method = "getRenderScaleFactor", at = @At("HEAD"), cancellable = true, require = 0)
    private void polyplus$nativeLobby(CallbackInfoReturnable<Double> cir) {
        if (LobbyScene.showing()) cir.setReturnValue(1.0);
    }

    @Inject(method = "setShouldScale", at = @At("HEAD"), cancellable = true, require = 0)
    private void polyplus$keepLobbyTarget(boolean shouldScale, CallbackInfo ci) {
        if (shouldScale) {
            polyplus$skippedScale = polyplus$targetReady && LobbyScene.showing();
            polyplus$targetReady = true;
            if (polyplus$skippedScale) ci.cancel();
        } else if (polyplus$skippedScale) {
            polyplus$skippedScale = false;
            ci.cancel();
        }
    }

    @Inject(method = "updateDynamicScale", at = @At("RETURN"), require = 0)
    private void polyplus$resizeForLobby(CallbackInfo ci) {
        boolean lobby = LobbyScene.showing();
        if (lobby == polyplus$lobbyNative) return;
        polyplus$lobbyNative = lobby;
        resizeRenderTarget();
    }
}
//?}
