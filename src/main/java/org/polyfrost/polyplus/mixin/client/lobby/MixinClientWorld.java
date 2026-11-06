package org.polyfrost.polyplus.mixin.client.lobby;

//? if = 1.8.9 {
/*import net.minecraft.client.world.ClientWorld;
import org.polyfrost.polyplus.client.lobby.LobbyCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class MixinClientWorld {
    @Inject(method = "disconnect()V", at = @At("HEAD"))
    private void polyplus$captureBeforeDisconnect(CallbackInfo ci) {
        LobbyCapture.onLeave();
    }
}
*///?}
