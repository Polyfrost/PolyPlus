package org.polyfrost.polyplus.mixin.client.lobby;

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.DeltaTracker;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public class MixinDeltaTrackerTimer {
    @Inject(method = "getGameTimeDeltaPartialTick", at = @At("HEAD"), cancellable = true)
    private void polyplus$drivenPartial(boolean runsNormally, CallbackInfoReturnable<Float> cir) {
        Float partial = LobbyScene.framePartial();
        if (partial != null) cir.setReturnValue(partial);
    }
}
*///?}
