package org.polyfrost.polyplus.mixin.client.nameplate.iris;

//? if >= 1.21.5 && < 26.3 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIrisTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

//? if >= 1.21.5 && < 26.3 {
/*@IfModLoaded("iris")
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
*///?}
public class MixinGlCommandEncoder {
    //? if >= 1.21.5 && < 26.3 {
    /*@Shadow
    private RenderPipeline lastPipeline;

    @Inject(method = "applyPipelineState", at = @At("HEAD"))
    private void polyplus$unlockShaderPackBlend(RenderPipeline pipeline, CallbackInfo ci) {
        if (NameplateIrisTarget.enabled) {
            NameplateIrisTarget.beforeApply(pipeline);
        }
    }

    @Inject(method = "trySetup", at = @At("RETURN"))
    private void polyplus$drawIntoShaderPack(CallbackInfoReturnable<Boolean> cir) {
        if (NameplateIrisTarget.enabled && cir.getReturnValueZ()) {
            NameplateIrisTarget.onSetup(this.lastPipeline);
        }
    }
    *///?}
}
