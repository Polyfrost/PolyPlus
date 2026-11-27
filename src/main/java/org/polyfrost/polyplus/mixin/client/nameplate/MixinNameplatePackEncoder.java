package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.5 {
import org.polyfrost.polyplus.client.nameplate.material.NameplateShaderPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >= 26.3 {
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

//? if >= 26.3 {
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
//?} else {
/*@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
*///?}
public class MixinNameplatePackEncoder {
    @Shadow
    //? if >= 26.3 {
    private GlRenderPipeline lastPipeline;
    //?} else {
    /*private RenderPipeline lastPipeline;
    *///?}

    //? if >= 26.3 {
    @Inject(method = "setupDraw", at = @At("RETURN"))
    private void polyplus$drawIntoShaderPack(CallbackInfo ci) {
        NameplateShaderPacks.redirect(this.lastPipeline);
    }
    //?} else {
    /*@Inject(method = "applyPipelineState", at = @At("HEAD"))
    private void polyplus$unlockShaderPackBlend(RenderPipeline pipeline, CallbackInfo ci) {
        NameplateShaderPacks.unlockBlend(pipeline);
    }

    @Inject(method = "trySetup", at = @At("RETURN"))
    private void polyplus$drawIntoShaderPack(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) NameplateShaderPacks.redirect(this.lastPipeline);
    }
    *///?}

    //? if >= 26.3 {
    @Inject(method = {"executeDraw", "executeDraws", "executeDrawIndirect"}, at = @At("TAIL"))
    //?} elif >= 26.2 {
    /*@Inject(method = {"executeDraw", "executeDrawMultiple", "executeDraws", "executeDrawIndirect"}, at = @At("TAIL"))
    *///?} else {
    /*@Inject(method = {"executeDraw", "executeDrawMultiple"}, at = @At("TAIL"))
    *///?}
    private void polyplus$restoreAfterDraw(CallbackInfo ci) {
        NameplateShaderPacks.restore();
    }

    //? if >= 26.2 {
    @Inject(method = "submitRenderPass", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "finishRenderPass", at = @At("HEAD"))
    *///?}
    private void polyplus$restoreAtPassEnd(CallbackInfo ci) {
        NameplateShaderPacks.restore();
    }
}
//?}
