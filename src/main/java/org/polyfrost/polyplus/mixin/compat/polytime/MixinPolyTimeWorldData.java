package org.polyfrost.polyplus.mixin.compat.polytime;

//? if = 1.8.9 {
/*import net.minecraft.world.WorldData;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldData.class)
public class MixinPolyTimeWorldData {
    @Shadow private long timeOfDay;

    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void polyplus$lobbyClock(CallbackInfoReturnable<Long> cir) {
        if (LobbyScene.terrainActive()) cir.setReturnValue(this.timeOfDay);
    }
}
*///?}
