package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIrisTarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

//? if >= 26.3 {
@Mixin(GlRenderPipeline.class)
//?}
public class MixinGlRenderPipeline {
    //? if >= 26.3 {
    @Shadow
    @Final
    private GlProgram program;

    @Inject(method = "bind", at = @At("HEAD"))
    private void polyplus$unlockShaderPackBlend(CallbackInfo ci) {
        if (NameplateIrisTarget.enabled) {
            NameplateIrisTarget.beforeBind(this.program);
        }
    }

    @Inject(method = "bind", at = @At("RETURN"))
    private void polyplus$drawIntoShaderPack(CallbackInfo ci) {
        if (NameplateIrisTarget.enabled) {
            NameplateIrisTarget.onBind(this.program);
        }
    }
    //?}
}
