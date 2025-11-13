package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.deftu.omnicore.api.client.screen.OmniScreen;
import org.polyfrost.oneconfig.internal.ui.OneConfigUI;
import org.polyfrost.polyplus.client.gui.LockerUI;
import org.polyfrost.polyui.component.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OneConfigUI.class)
public class Mixin_AddCosmeticsPage {
    @Inject(
            method = "create(Lorg/polyfrost/polyui/component/Component;)Ldev/deftu/omnicore/api/client/screen/OmniScreen;",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/polyfrost/polyui/component/extensions/ExtensionsKt;padded(Lorg/polyfrost/polyui/component/Component;FFFF)Lorg/polyfrost/polyui/component/Component;",
                    ordinal = 4,
                    remap = false
            ),
            remap = false
    )
    private void polyplus$addCosmeticsButton(
            Component initialScreen,
            CallbackInfoReturnable<OmniScreen> cir,
            @Local Component[] var10005
    ) {
        // Push index 8 and onwards one position to the right
        for (int i = var10005.length - 1; i > 8; i--) {
            var10005[i] = var10005[i - 1];
        }

        // Insert at index 8
        var10005[8] = LockerUI.createCosmeticsPageButton();
    }
}
