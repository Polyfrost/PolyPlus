package org.polyfrost.polyplus.mixin.client.lobby;

//? if < 1.21.5 && > 1.8.9 {
/*import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.polyfrost.polyplus.client.lobby.LobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderStateShard.class)
public class MixinRenderStateShard {
    @Inject(method = "setupRenderState", at = @At("TAIL"))
    private void polyplus$hologramState(CallbackInfo ci) {
        if ((Object) this instanceof RenderType) LobbyPipelines.hologramState();
    }
}
*///?}
