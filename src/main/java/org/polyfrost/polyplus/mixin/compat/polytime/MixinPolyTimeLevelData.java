package org.polyfrost.polyplus.mixin.compat.polytime;

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.multiplayer.ClientLevel;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.ClientLevelData.class)
public class MixinPolyTimeLevelData {
    @Shadow private long dayTime;

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void polyplus$lobbyClock(CallbackInfoReturnable<Long> cir) {
        if (LobbyScene.terrainActive()) cir.setReturnValue(this.dayTime);
    }
}
*///?}
