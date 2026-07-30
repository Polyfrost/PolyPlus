package org.polyfrost.polyplus.mixin.client.essential;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "gg.essential.partnermod.PartnerModConfig", remap = false)
public class Mixin_EssentialPartnerConfig {
    @Inject(method = "shouldHideButtons", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$alwaysHideButtons(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
