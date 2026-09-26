package org.polyfrost.polyplus.mixin.compat.oneconfig;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.polyfrost.oneconfig.internal.ui.api.TreeConfigData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(value = TreeConfigData.class, remap = false)
public abstract class MixinTreeConfigData {
    private static final Set<String> POLYPLUS$MOD_ICONS = Set.of(
            "animatium", "betternightvision", "betterscreens", "blur", "confirmdisconnect", "crosshairtweaks",
            "droppeditemtweaks", "fastquit", "gammautils", "mountopacity", "numericalenchantments", "overlaytweaks",
            "rendertweaks", "sciophobia", "shaketweaks", "simplenickhider", "smoothskies", "tooltipscroll",
            "waveycapes", "zoomify"
    );

    @ModifyReturnValue(method = "getIcon", at = @At("RETURN"), remap = false)
    private String polyplus$modCardIcon(String original) {
        String id = ((TreeConfigData) (Object) this).getId().replaceFirst("(v\\d+)?\\.json$", "");
        return POLYPLUS$MOD_ICONS.contains(id) ? "assets/polyplus/modicons/" + id + ".svg" : original;
    }
}
