package org.polyfrost.polyplus.mixin.compat.iris;

//? if >= 26.2 {
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.VanillaRenderingPipeline", remap = false)
public class MixinVanillaRenderingPipeline {
    @Inject(method = "beginLevelRendering", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void polyplus$skipUseProgram(CallbackInfo ci) {
        ci.cancel();
    }
}
//?}
