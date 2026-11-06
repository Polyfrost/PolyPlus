package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.lwjgl.opengl.GL20;
import org.polyfrost.polyplus.client.lobby.LobbySodiumWave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.ShaderChunkRenderer", remap = false)
public class MixinCeleritasShaderChunkRendererLobby {
    @Unique
    private static final String POLYPLUS_LOBBY_SHADER = "polyplus_lobby/blocks/block_layer_opaque";

    @Unique
    private static final String POLYPLUS_WAVE_UNIFORM = "u_PolyLobbyWave";

    @Unique
    private static MethodHandle polyplus$createShader;

    @Unique
    private static MethodHandle polyplus$activeProgram;

    @Unique
    private final Map<ChunkShaderOptions, GlProgram<?>> polyplus$lobbyPrograms = new HashMap<>();

    @Unique
    private final Map<GlProgram<?>, Integer> polyplus$waveUniforms = new IdentityHashMap<>();

    @Inject(method = "compileProgram(Lorg/embeddedt/embeddium/impl/render/chunk/shader/ChunkShaderOptions;)Lorg/embeddedt/embeddium/impl/gl/shader/GlProgram;", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$lobbyProgram(ChunkShaderOptions options, CallbackInfoReturnable<GlProgram<?>> cir) {
        if (!LobbySodiumWave.active()) return;
        GlProgram<?> program = polyplus$lobbyPrograms.get(options);
        if (program == null && !polyplus$lobbyPrograms.containsKey(options)) {
            program = polyplus$build(options);
            polyplus$lobbyPrograms.put(options, program);
        }
        if (program != null) cir.setReturnValue(program);
    }

    @Inject(method = "begin(Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;)V", at = @At("TAIL"), remap = false, require = 0, expect = 0)
    private void polyplus$lobbyWave(TerrainRenderPass pass, CallbackInfo ci) {
        if (polyplus$waveUniforms.isEmpty() || !LobbySodiumWave.active()) return;
        Integer uniform;
        try {
            uniform = polyplus$waveUniforms.get((GlProgram<?>) polyplus$activeProgram.invoke((Object) this));
        } catch (Throwable error) {
            LobbySodiumWave.fail("Celeritas active program", error);
            return;
        }
        if (uniform != null) GL20.glUniform2f(uniform, LobbySodiumWave.seconds(), LobbySodiumWave.aim());
    }

    @Inject(method = "delete(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;)V", at = @At("TAIL"), remap = false, require = 0, expect = 0)
    private void polyplus$deleteLobbyPrograms(CommandList commandList, CallbackInfo ci) {
        for (GlProgram<?> program : polyplus$lobbyPrograms.values()) {
            if (program != null) program.delete();
        }
        polyplus$lobbyPrograms.clear();
        polyplus$waveUniforms.clear();
    }

    @Unique
    private GlProgram<?> polyplus$build(ChunkShaderOptions options) {
        GlProgram<?> program;
        try {
            if (polyplus$createShader == null) {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                polyplus$activeProgram = lookup.findGetter(lookup.lookupClass(), "activeProgram", GlProgram.class);
                polyplus$createShader = lookup.findVirtual(lookup.lookupClass(), "createShader", MethodType.methodType(GlProgram.class, String.class, ChunkShaderOptions.class));
            }
            program = (GlProgram<?>) polyplus$createShader.invoke((Object) this, POLYPLUS_LOBBY_SHADER, options);
        } catch (Throwable error) {
            LobbySodiumWave.fail("Celeritas lobby program", error);
            return null;
        }
        if (!LobbySodiumWave.active()) {
            program.delete();
            return null;
        }
        int uniform = GL20.glGetUniformLocation(program.handle(), POLYPLUS_WAVE_UNIFORM);
        if (uniform < 0) {
            program.delete();
            LobbySodiumWave.fail("Celeritas lobby uniform", null);
            return null;
        }
        polyplus$waveUniforms.put(program, uniform);
        return program;
    }
}
*///?}
