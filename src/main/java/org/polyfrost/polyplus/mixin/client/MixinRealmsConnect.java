package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.realms.RealmsConnect;
import org.polyfrost.polyplus.client.gui.screens.connection.ConnectionTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RealmsConnect.class)
public class MixinRealmsConnect {
    @Inject(method = "connect", at = @At("HEAD"))
    private void polyplus$forgetServer(CallbackInfo ci) {
        ConnectionTarget.note(null, null);
    }
}
//?}
