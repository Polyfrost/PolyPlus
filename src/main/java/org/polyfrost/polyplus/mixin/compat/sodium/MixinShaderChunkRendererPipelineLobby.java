package org.polyfrost.polyplus.mixin.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if >= 26.2 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
//? if >= 26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.lobby.LobbySodiumUniforms;
import org.polyfrost.polyplus.client.lobby.LobbySodiumWave;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class MixinShaderChunkRendererPipelineLobby {
    //? if >= 26.2 {
    //? if >= 26.3 {
    @Unique
    private static final String polyplus$BUILDER = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;";
    @Unique
    private static final String polyplus$PIPELINE = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;";
    @Unique
    private static final String polyplus$SNIPPETS = "[Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Snippet;";
    //?} else {
    /*@Unique
    private static final String polyplus$BUILDER = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;";
    @Unique
    private static final String polyplus$PIPELINE = "Lcom/mojang/blaze3d/pipeline/RenderPipeline;";
    @Unique
    private static final String polyplus$SNIPPETS = "[Lcom/mojang/blaze3d/pipeline/RenderPipeline$Snippet;";
    *///?}
    @Unique
    private static final String polyplus$IDENTIFIER = "Lnet/minecraft/resources/Identifier;";
    @Unique
    private static final String polyplus$SHADER = "blocks/block_layer_opaque";
    @Unique
    private static final String polyplus$PASS = "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;";
    @Unique
    private static final String polyplus$OIT_STAGE = "Lnet/minecraft/client/renderer/oit/OitStage;";
    @Unique
    private static final String polyplus$CREATE = "createShader(Ljava/lang/String;" + polyplus$PASS + ")" + polyplus$PIPELINE;
    @Unique
    private static final String polyplus$CREATE_OIT = "createOITShader(Ljava/lang/String;" + polyplus$PASS + polyplus$OIT_STAGE + ")Lnet/minecraft/client/renderer/oit/OitPipelineSet;";

    @Unique
    private static final Map<TerrainRenderPass, RenderPipeline> polyplus$programs = new HashMap<>();
    //? if >= 26.3
    @Unique private static final Map<TerrainRenderPass, OitPipelineSet> polyplus$oitPrograms = new HashMap<>();
    @Unique
    private static boolean polyplus$building;

    //? if >= 26.3 {
    @Inject(method = "compileProgram(" + polyplus$PASS + polyplus$OIT_STAGE + ")" + polyplus$PIPELINE, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void polyplus$lobbyProgram(TerrainRenderPass pass, OitStage stage, CallbackInfoReturnable<RenderPipeline> cir) {
        if (!LobbySodiumWave.active()) return;
        RenderPipeline program = LobbySodiumUniforms.ready() ? this.polyplus$program(pass, stage) : null;
        LobbySodiumUniforms.bound = program != null;
        if (program != null) cir.setReturnValue(program);
    }
    //?} else {
    /*@Inject(method = "compileProgram(" + polyplus$PASS + ")" + polyplus$PIPELINE, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void polyplus$lobbyProgram(TerrainRenderPass pass, CallbackInfoReturnable<RenderPipeline> cir) {
        if (!LobbySodiumWave.active()) return;
        RenderPipeline program = LobbySodiumUniforms.ready() ? this.polyplus$program(pass) : null;
        LobbySodiumUniforms.bound = program != null;
        if (program != null) cir.setReturnValue(program);
    }
    *///?}

    //? if >= 26.3 {
    @Unique
    private RenderPipeline polyplus$program(TerrainRenderPass pass, OitStage stage) {
        try {
            RenderPipeline program;
            if (stage == null) {
                program = polyplus$programs.get(pass);
                if (program == null) {
                    program = (RenderPipeline) this.polyplus$build("createShader", new Class<?>[]{String.class, TerrainRenderPass.class}, polyplus$SHADER, pass);
                    polyplus$programs.put(pass, program);
                    LobbySodiumUniforms.built(program.getLocation());
                }
            } else {
                OitPipelineSet set = polyplus$oitPrograms.get(pass);
                if (set == null) {
                    set = (OitPipelineSet) this.polyplus$build("createOITShader", new Class<?>[]{String.class, TerrainRenderPass.class, OitStage.class}, polyplus$SHADER, pass, stage);
                    polyplus$oitPrograms.put(pass, set);
                    LobbySodiumUniforms.built(set.accumulatePipeline().getLocation());
                }
                program = set.getPipeline(stage);
            }
            if (RenderSystem.getCompiledPipelineNullable(program) != null) return program;
            LobbySodiumWave.fail("Sodium lobby pipeline " + program.getLocation() + " did not compile", null);
        } catch (Throwable error) {
            LobbySodiumWave.fail("Sodium lobby pipeline", error);
        }
        return null;
    }
    //?} else {
    /*@Unique
    private RenderPipeline polyplus$program(TerrainRenderPass pass) {
        try {
            RenderPipeline program = polyplus$programs.get(pass);
            if (program == null) {
                program = (RenderPipeline) this.polyplus$build("createShader", new Class<?>[]{String.class, TerrainRenderPass.class}, polyplus$SHADER, pass);
                polyplus$programs.put(pass, program);
                LobbySodiumUniforms.built(program.getLocation());
            }
            if (RenderSystem.getDevice().precompilePipeline(program).isValid()) return program;
            LobbySodiumWave.fail("Sodium lobby pipeline " + program.getLocation() + " did not compile", null);
        } catch (Throwable error) {
            LobbySodiumWave.fail("Sodium lobby pipeline", error);
        }
        return null;
    }
    *///?}

    @Unique
    private Object polyplus$build(String name, Class<?>[] types, Object... args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle handle = lookup.unreflect(lookup.lookupClass().getDeclaredMethod(name, types)).bindTo(this);
        polyplus$building = true;
        try {
            return handle.invokeWithArguments(args);
        } finally {
            polyplus$building = false;
        }
    }

    @ModifyExpressionValue(
            method = {polyplus$CREATE, polyplus$CREATE_OIT},
            at = @At(value = "INVOKE", target = polyplus$PIPELINE + "builder(" + polyplus$SNIPPETS + ")" + polyplus$BUILDER),
            remap = false,
            require = 0
    )
    private RenderPipeline.Builder polyplus$waveGroup(RenderPipeline.Builder builder) {
        return polyplus$building ? builder.withBindGroupLayout(LobbySodiumUniforms.LAYOUT) : builder;
    }

    @ModifyArg(
            method = {polyplus$CREATE, polyplus$CREATE_OIT},
            at = @At(value = "INVOKE", target = polyplus$BUILDER + "withLocation(" + polyplus$IDENTIFIER + ")" + polyplus$BUILDER),
            remap = false,
            require = 0
    )
    private Identifier polyplus$location(Identifier id) {
        return polyplus$building ? LobbySodiumUniforms.lobbyId(id) : id;
    }

    @ModifyArg(
            method = {polyplus$CREATE, polyplus$CREATE_OIT},
            at = @At(value = "INVOKE", target = polyplus$BUILDER + "withVertexShader(" + polyplus$IDENTIFIER + ")" + polyplus$BUILDER),
            remap = false,
            require = 0
    )
    private Identifier polyplus$vertex(Identifier id) {
        return polyplus$building ? LobbySodiumUniforms.lobbyId(id) : id;
    }

    @ModifyArg(
            method = {polyplus$CREATE, polyplus$CREATE_OIT},
            at = @At(value = "INVOKE", target = polyplus$BUILDER + "withFragmentShader(" + polyplus$IDENTIFIER + ")" + polyplus$BUILDER),
            remap = false,
            require = 0
    )
    private Identifier polyplus$fragment(Identifier id) {
        return polyplus$building ? LobbySodiumUniforms.lobbyId(id) : id;
    }

    //? if >= 26.3 {
    @ModifyArg(
            method = polyplus$CREATE_OIT,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/oit/OitPipelineSet;builder(Ljava/lang/String;" + polyplus$BUILDER + ")Lnet/minecraft/client/renderer/oit/OitPipelineSet$Builder;"
            ),
            index = 0,
            remap = false,
            require = 0
    )
    private String polyplus$oitSuffix(String suffix) {
        return polyplus$building ? LobbySodiumUniforms.lobbySuffix(suffix) : suffix;
    }
    //?}
    //?}
}
