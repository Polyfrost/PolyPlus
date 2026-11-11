package org.polyfrost.polyplus.mixin.compat.polytime;

//? if >= 26.1 && < 26.3 {
/*import java.util.Map;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public class MixinPolyTimeClockManager {
    @Shadow @Final private Map<Holder<WorldClock>, ?> clocks;

    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    private void polyplus$lobbyClock(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
        if (!LobbyScene.terrainActive()) return;
        Object clock = this.clocks.get(definition);
        cir.setReturnValue(clock == null ? 0L : ((PolyTimeClockInstanceAccessor) clock).polyplus$totalTicks());
    }
}
*///?}
