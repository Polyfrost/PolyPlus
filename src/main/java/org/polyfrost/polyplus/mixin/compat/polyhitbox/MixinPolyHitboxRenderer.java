package org.polyfrost.polyplus.mixin.compat.polyhitbox;

import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.polyfrost.polyhitbox.render.HitboxRenderer", remap = false)
public class MixinPolyHitboxRenderer {
    @Inject(method = "beginFrame", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$hideInLobby(CallbackInfoReturnable<Boolean> cir) {
        if (LobbyScene.terrainActive()) cir.setReturnValue(false);
    }
}
