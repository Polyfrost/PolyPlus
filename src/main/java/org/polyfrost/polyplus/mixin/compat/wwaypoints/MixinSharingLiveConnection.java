package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import com.wwaypoints.client.sharing.SharingAuthService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the backend connection only matters to users with Patreon-powered waypoint sharing
@Pseudo
@IfModLoaded(value = "wwaypoints", minVersion = "0.8.1")
@Mixin(targets = "com.wwaypoints.client.sharing.SharingLiveConnection", remap = false)
public class MixinSharingLiveConnection {
    @Inject(method = {"announceConnected", "announceDropped", "announceExhausted"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void polyplus$skipToastWithoutSharing(CallbackInfo ci) {
        if (!SharingAuthService.get().snapshot().paid()) ci.cancel();
    }
}
