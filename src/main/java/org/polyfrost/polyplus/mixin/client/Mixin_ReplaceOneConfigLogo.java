package org.polyfrost.polyplus.mixin.client;

import org.polyfrost.oneconfig.internal.ui.OneConfigUI;
import org.polyfrost.polyplus.client.PolyPlusClient;
import org.polyfrost.polyui.data.PolyImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(OneConfigUI.class)
public class Mixin_ReplaceOneConfigLogo {
    @ModifyArg(
            method = "create(Lorg/polyfrost/polyui/component/Component;)Ldev/deftu/omnicore/api/client/screen/OmniScreen;",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/polyfrost/polyui/component/impl/Image;<init>(Lorg/polyfrost/polyui/data/PolyImage;JJLorg/polyfrost/polyui/color/PolyColor;Lorg/polyfrost/polyui/unit/Align;[FZ[Lorg/polyfrost/polyui/component/Component;ILkotlin/jvm/internal/DefaultConstructorMarker;)V",
                    ordinal = 0,
                    remap = false
            ),
            remap = false
    )
    private PolyImage polyplus$modifyLogoPath(PolyImage original) {
        return PolyPlusClient.getOneClientLogo();
    }
}
