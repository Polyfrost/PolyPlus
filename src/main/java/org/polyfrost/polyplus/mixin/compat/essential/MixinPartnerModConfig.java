package org.polyfrost.polyplus.mixin.compat.essential;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "gg.essential.partnermod.PartnerModConfig", remap = false)
public class MixinPartnerModConfig {
    @ModifyReturnValue(method = "shouldHideButtons", at = @At("RETURN"), remap = false, require = 0, expect = 0)
    private boolean polyplus$alwaysHideButtons(boolean original) {
        return true;
    }
}
