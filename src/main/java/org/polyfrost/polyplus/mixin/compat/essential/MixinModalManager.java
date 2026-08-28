package org.polyfrost.polyplus.mixin.compat.essential;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "gg.essential.partnermod.modal.ModalManager", remap = false)
public class MixinModalManager {
    @WrapMethod(method = "registerEvents", remap = false, require = 0, expect = 0)
    private void polyplus$noModalEvents(Operation<Void> original) {
    }

    @WrapMethod(method = "setModal", remap = false, require = 0, expect = 0)
    private void polyplus$noModal(@Coerce Object modal, Operation<Void> original) {
    }
}
