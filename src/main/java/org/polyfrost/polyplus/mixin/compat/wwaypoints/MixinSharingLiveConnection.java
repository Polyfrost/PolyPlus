package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

// the backend dropping and reconnecting on its own isn't actionable, unlike exhausting its retries
// NoticeSink is nested in a package-private class, hence the coerced receivers
@Pseudo
@IfModLoaded(value = "wwaypoints", minVersion = "0.8.1")
@Mixin(targets = "com.wwaypoints.client.sharing.SharingLiveConnection", remap = false)
public class MixinSharingLiveConnection {
    @WrapWithCondition(
            method = "announceConnected",
            at = @At(value = "INVOKE", target = "Lcom/wwaypoints/client/sharing/SharingLiveConnection$NoticeSink;connected(Z)V"),
            remap = false
    )
    private boolean polyplus$skipConnectedToast(@Coerce Object notices, boolean reconnected) {
        return false;
    }

    @WrapWithCondition(
            method = "announceDropped",
            at = @At(value = "INVOKE", target = "Lcom/wwaypoints/client/sharing/SharingLiveConnection$NoticeSink;dropped()V"),
            remap = false
    )
    private boolean polyplus$skipDroppedToast(@Coerce Object notices) {
        return false;
    }
}
