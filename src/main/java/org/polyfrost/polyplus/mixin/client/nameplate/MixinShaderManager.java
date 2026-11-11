package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.mojang.blaze3d.pipeline.PipelineCache;
import net.minecraft.client.renderer.ShaderManager;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
//?} elif = 26.2 {
/*import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} elif >= 1.21.11 {
/*import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialPipelines;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateShaderSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} elif >= 1.21.8 {
/*import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialPipelines;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateShaderSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} elif >= 1.21.4 {
/*import net.minecraft.client.renderer.ShaderManager;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

//? if >= 1.21.4 {
@Mixin(ShaderManager.class)
//?}
public class MixinShaderManager {
    //? if >= 26.3 {
    @ModifyArg(
        method = "apply",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setCurrentPipelineCache(Lcom/mojang/blaze3d/pipeline/PipelineCache;)Lcom/mojang/blaze3d/pipeline/PipelineCache;"
        )
    )
    private PipelineCache polyplus$compileNameplateMaterials(PipelineCache cache) {
        NameplateMaterialPipelines.onShadersApplied(cache);
        return cache;
    }
    //?} elif = 26.2 {
    /*@Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void polyplus$serveNameplateShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        String source = NameplateMaterialPipelines.shader(id, type);
        if (source != null) {
            cir.setReturnValue(source);
        }
    }

    @Inject(
        method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("TAIL")
    )
    private void polyplus$compileNameplateMaterials(CallbackInfo ci) {
        NameplateMaterialPipelines.onShadersApplied();
    }
    *///?} elif >= 1.21.11 {
    /*@Inject(
        method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("TAIL")
    )
    private void polyplus$compileNameplateMaterials(CallbackInfo ci) {
        NameplateMaterialPipelines.onShadersApplied();
    }

    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void polyplus$serveNameplateShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        String source = NameplateShaderSources.shader(id, type);
        if (source != null) {
            cir.setReturnValue(source);
        }
    }
    *///?} elif >= 1.21.8 {
    /*@Inject(
        method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("TAIL")
    )
    private void polyplus$compileNameplateMaterials(CallbackInfo ci) {
        NameplateMaterialPipelines.onShadersApplied();
    }

    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void polyplus$serveNameplateShader(ResourceLocation id, ShaderType type, CallbackInfoReturnable<String> cir) {
        String source = NameplateShaderSources.shader(id, type);
        if (source != null) {
            cir.setReturnValue(source);
        }
    }
    *///?} elif >= 1.21.4 {
    /*@Inject(
        method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("TAIL")
    )
    private void polyplus$compileNameplateMaterials(CallbackInfo ci) {
        NameplateMaterialPipelines.onShadersApplied();
    }
    *///?}
}
