package org.polyfrost.polyplus.mixin.client.essential;

import java.util.List;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "gg.essential.partnermod.EssentialPartner", remap = false)
public class Mixin_EssentialPartnerButton {
    @Inject(method = "createButton", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$noAdButton(@Coerce Object screen, List<?> widgets, BiConsumer<?, ?> adder, CallbackInfo ci) {
        ci.cancel();
    }
}
