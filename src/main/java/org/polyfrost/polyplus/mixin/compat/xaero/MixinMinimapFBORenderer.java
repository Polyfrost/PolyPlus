package org.polyfrost.polyplus.mixin.compat.xaero;

//? if xaerominimap && < 1.21.6 {
/*import com.llamalad7.mixinextras.sugar.Local;
import org.polyfrost.polyplus.compat.XaeroMinimapRefreshCap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.graphics.ImprovedFramebuffer;

import java.util.Objects;

@Pseudo
@Mixin(targets = "xaero.common.minimap.render.MinimapFBORenderer", remap = false)
public class MixinMinimapFBORenderer {
    @Shadow
    private ImprovedFramebuffer rotationFramebuffer;

    @Unique
    private final XaeroMinimapRefreshCap polyplus$refreshCap = new XaeroMinimapRefreshCap();

    @Inject(method = "renderChunksToFBO", at = @At("HEAD"), cancellable = true, remap = false)
    private void polyplus$reuseFramebuffer(
            CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) int viewW,
            @Local(argsOnly = true, ordinal = 2) int shape,
            @Local(argsOnly = true, ordinal = 1) boolean lockedNorth,
            @Local(argsOnly = true, ordinal = 2) boolean cave
    ) {
        int key = Objects.hash(System.identityHashCode(rotationFramebuffer), viewW, shape, lockedNorth, cave);
        if (polyplus$refreshCap.reuse(key)) ci.cancel();
    }
}
*///?}
