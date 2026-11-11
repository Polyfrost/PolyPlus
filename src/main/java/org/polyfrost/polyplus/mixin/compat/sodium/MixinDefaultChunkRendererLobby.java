package org.polyfrost.polyplus.mixin.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if >= 26.2 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//? if >= 26.3 {
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
*///?}
import org.polyfrost.polyplus.client.lobby.LobbySodiumUniforms;
import org.polyfrost.polyplus.client.lobby.LobbySodiumWave;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public class MixinDefaultChunkRendererLobby {
    //? if >= 26.2 {
    //? if >= 26.3 {
    @Inject(method = "prepare", at = @At("HEAD"), remap = false, require = 0)
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"), remap = false, require = 0)
    *///?}
    private void polyplus$uploadWave(CallbackInfo ci) {
        if (LobbySodiumWave.active()) LobbySodiumUniforms.upload();
    }

    //? if >= 26.3 {
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setPipeline(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V"
            ),
            remap = false,
            require = 0
    )
    private void polyplus$bindWave(RenderPass pass, CompiledRenderPipeline pipeline, Operation<Void> original) {
        original.call(pass, pipeline);
        LobbySodiumUniforms.bind(pass);
    }
    //?} else {
    /*@WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"
            ),
            remap = false,
            require = 0
    )
    private void polyplus$bindWave(RenderPass pass, RenderPipeline pipeline, Operation<Void> original) {
        original.call(pass, pipeline);
        LobbySodiumUniforms.bind(pass);
    }
    *///?}
    //?}
}
