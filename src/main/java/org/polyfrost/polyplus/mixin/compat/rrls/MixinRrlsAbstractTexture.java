package org.polyfrost.polyplus.mixin.compat.rrls;

//? if >= 26.2 {
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.polyfrost.polyplus.compat.RrlsCrashGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// RRLS replaces any texture whose GPU object is a null with the missing texture  regardless of its overlay state
// suppress that on Vulkan to avoid crashes (see RrlsCrashGuard for the other part of this)
@Mixin(value = AbstractTexture.class, priority = 1500)
public class MixinRrlsAbstractTexture {
    private static final String RRLS_MIXIN = "org.redlance.dima_dencep.mods.rrls.mixins.workaround.textures.AbstractTextureMixin";

    @TargetHandler(mixin = RRLS_MIXIN, name = "rrls$useMissingTexture")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void polyplus$noMissingTextureOnVulkan(CallbackInfoReturnable<?> originalCir, CallbackInfo ci) {
        if (RrlsCrashGuard.active()) ci.cancel();
    }

    @TargetHandler(mixin = RRLS_MIXIN, name = "rrls$useMissingTextureView")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void polyplus$noMissingTextureViewOnVulkan(CallbackInfoReturnable<?> originalCir, CallbackInfo ci) {
        if (RrlsCrashGuard.active()) ci.cancel();
    }
}
//?}
