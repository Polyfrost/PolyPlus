package org.polyfrost.polyplus.mixin.client;

import dev.deftu.omnicore.api.OmniCore;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import org.polyfrost.polyplus.client.events.LevelLoadEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldClient.class)
public class Mixin_LevelLoadEvent {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyplus$onLevelLoad(CallbackInfo ci) {
        OmniCore.getEventBus().post(new LevelLoadEvent((World) (Object) this));
    }
}
