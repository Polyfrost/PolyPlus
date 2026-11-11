package org.polyfrost.polyplus.mixin.compat.spatialgui;

//? if > 1.8.9 {
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.client.SpatialGUIClient", remap = false)
public class MixinSpatialGUIClient {
    @Inject(method = "shouldHookScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private static void polyplus$skipLobby(CallbackInfoReturnable<Boolean> cir) {
        if (LobbyScene.showing()) cir.setReturnValue(false);
    }
}
//?}
