package org.polyfrost.polyplus.mixin.compat.essential;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "gg.essential.partnermod.EssentialPartner", remap = false)
public class MixinEssentialPartner {
    @WrapMethod(method = "createButton", remap = false, require = 0, expect = 0)
    private void polyplus$noAdButton(@Coerce Object screen, List<?> widgets, BiConsumer<?, ?> adder, Operation<Void> original) {
    }
}
