package org.polyfrost.polyplus.mixin.client.lobby;

//? if >= 1.21.8 && < 26.2 {
/*import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenOverlay {
    @Inject(method = "render3dCrosshair", at = @At("HEAD"), cancellable = true)
    private void polyplus$hideLobbyCrosshair(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }
}
*///?}
