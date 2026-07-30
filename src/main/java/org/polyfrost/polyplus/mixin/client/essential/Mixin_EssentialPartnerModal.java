package org.polyfrost.polyplus.mixin.client.essential;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "gg.essential.partnermod.modal.ModalManager", remap = false)
public class Mixin_EssentialPartnerModal {
    @Inject(method = "registerEvents", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$noModalEvents(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "setModal", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$noModal(@Coerce Object modal, CallbackInfo ci) {
        ci.cancel();
    }
}
