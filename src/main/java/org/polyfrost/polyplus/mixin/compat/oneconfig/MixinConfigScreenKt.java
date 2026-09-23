package org.polyfrost.polyplus.mixin.compat.oneconfig;

import androidx.compose.runtime.Composer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kotlin.Unit;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.polyplus.compat.WWaypointsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.screens.ConfigScreenKt", remap = false)
public class MixinConfigScreenKt {
    @WrapMethod(method = "SettingRow", remap = false)
    private static void polyplus$explainDisabledRow(Property<?> prop, boolean compact, Composer composer, int changed, int defaults, Operation<Void> original) {
        String reason = WWaypointsCompat.disabledReason(prop);
        if (reason == null) {
            original.call(prop, compact, composer, changed, defaults);
            return;
        }
        WWaypointsCompat.DisabledReasonTooltip(reason, (innerComposer, innerChanged) -> {
            original.call(prop, compact, innerComposer, changed, defaults);
            return Unit.INSTANCE;
        }, composer, 0);
    }
}
