package org.polyfrost.polyplus.mixin.compat.distanthorizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if dh {
import org.polyfrost.polyplus.compat.distanthorizons.DhLobbyGuard;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.SharedApi", remap = false)
public abstract class MixinDhSharedApi {
    //? if dh {
    @Inject(
            method = "applyChunkUpdate(Lcom/seibel/distanthorizons/core/wrapperInterfaces/chunk/IChunkWrapper;Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0,
            expect = 0
    )
    private void polyplus$ignoreLobbyChunks(@Coerce Object chunk, @Coerce Object level, boolean waitForLoadedWorld, CallbackInfo ci) {
        if (DhLobbyGuard.isLobby(level)) {
            ci.cancel();
        }
    }
    //?}
}
