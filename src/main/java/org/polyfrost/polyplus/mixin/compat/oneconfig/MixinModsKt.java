package org.polyfrost.polyplus.mixin.compat.oneconfig;

import androidx.compose.runtime.Composer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kotlin.Unit;
import org.polyfrost.polyplus.client.gui.ModOrderToggle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.navigation.graph.ComposableSingletons$ModsKt", remap = false)
public class MixinModsKt {
    @WrapOperation(
        method = "*",
        at = @At(value = "INVOKE", target = "Lorg/polyfrost/oneconfig/internal/ui/screens/ModsKt;Mods(Landroidx/compose/runtime/Composer;I)V"),
        remap = false
    )
    private static void polyplus$addModOrderToggle(Composer composer, int changed, Operation<Void> original) {
        ModOrderToggle.Overlay((innerComposer, innerChanged) -> {
            original.call(innerComposer, changed);
            return Unit.INSTANCE;
        }, composer, 0);
    }
}
