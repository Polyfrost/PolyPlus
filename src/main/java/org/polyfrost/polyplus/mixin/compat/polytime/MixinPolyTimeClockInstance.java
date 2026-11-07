package org.polyfrost.polyplus.mixin.compat.polytime;

//? if >= 26.3 {
import net.minecraft.client.ClientClockManager;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.ClientClockInstance.class)
public class MixinPolyTimeClockInstance {
    @Shadow private long totalTicks;

    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
    private void polyplus$lobbyClock(CallbackInfoReturnable<Long> cir) {
        if (LobbyScene.terrainActive()) cir.setReturnValue(this.totalTicks);
    }
}
//?}
