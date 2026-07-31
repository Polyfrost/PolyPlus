package org.polyfrost.polyplus.mixin;

import net.minecraft.CrashReport;
import org.polyfrost.polyplus.client.PolyPlusSentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrashReport.class)
public class Mixin_SentryCrashReport {
    @Inject(
            method = "<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
            at = @At("TAIL")
    )
    private void polyplus$reportToSentry(String title, Throwable cause, CallbackInfo ci) {
        if (cause != null) {
            PolyPlusSentry.captureCrashReport(title, cause);
        }
    }
}
