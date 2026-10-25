package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@IfModLoaded(value = "wwaypoints", minVersion = "0.8.1")
@Mixin(targets = "com.wwaypoints.client.screen.UiToast", remap = false)
public class MixinUiToast {
    @ModifyArg(
            method = "post(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/wwaypoints/client/screen/UiToast$Level;Lcom/wwaypoints/client/screen/UiToast$Icon;J)V",
            at = @At(value = "INVOKE", target = "Lcom/wwaypoints/client/screen/UiToast;<init>(Ljava/lang/String;Ljava/lang/String;Lcom/wwaypoints/client/screen/UiToast$Level;Lcom/wwaypoints/client/screen/UiToast$Icon;JJ)V"),
            index = 0,
            remap = false
    )
    private static String polyplus$brandTitle(String title) {
        // the short post overload already titles its toasts wWaypoints
        return title.isBlank() || title.equals("wUtilities") || title.equals("wWaypoints") ? "wWaypoints" : "wWaypoints - " + title;
    }
}
