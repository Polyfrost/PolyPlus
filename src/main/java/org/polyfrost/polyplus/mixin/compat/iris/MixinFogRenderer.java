package org.polyfrost.polyplus.mixin.compat.iris;

//? if >= 26.2 {
import com.bawnorton.mixinsquared.TargetHandler;
import net.irisshaders.iris.Iris;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FogRenderer.class, priority = 1500)
public class MixinFogRenderer {
    @TargetHandler(mixin = "net.irisshaders.iris.mixin.MixinFogRenderer", name = "iris$setupLegacyWaterFog")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void polyplus$skipWithoutPack(CallbackInfo ci) {
        if (!Iris.isPackInUseQuick()) ci.cancel();
    }
}
//?}
