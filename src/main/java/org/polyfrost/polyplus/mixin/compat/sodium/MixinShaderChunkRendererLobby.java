package org.polyfrost.polyplus.mixin.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if sodium && < 26.2 {
/*import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat2v;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.polyfrost.polyplus.client.lobby.LobbySodiumWave;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class MixinShaderChunkRendererLobby {
    //? if sodium && < 26.2 {
    /*@Unique
    private static final Logger polyplus$LOGGER = LogManager.getLogger("PolyPlus/Lobby");
    @Unique
    private static final String polyplus$LOBBY_PATH = "polyplus_lobby/block_layer_opaque";
    @Unique
    private static final String polyplus$WAVE_UNIFORM = "u_PolyLobbyWave";

    @Unique
    private static boolean polyplus$resolved;
    @Unique
    private static MethodHandle polyplus$createShader;
    @Unique
    private static MethodHandle polyplus$activeProgram;

    @Unique
    private final Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> polyplus$lobbyPrograms = new HashMap<>();
    @Unique
    private final Map<GlProgram<ChunkShaderInterface>, GlUniformFloat2v> polyplus$waveUniforms = new IdentityHashMap<>();

    @Unique
    private static boolean polyplus$resolve() {
        if (!polyplus$resolved) {
            polyplus$resolved = true;
            try {
                Method create = ShaderChunkRenderer.class.getDeclaredMethod("createShader", String.class, ChunkShaderOptions.class);
                create.setAccessible(true);
                Field active = ShaderChunkRenderer.class.getDeclaredField("activeProgram");
                active.setAccessible(true);
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                polyplus$activeProgram = lookup.unreflectGetter(active);
                polyplus$createShader = lookup.unreflect(create);
            } catch (Throwable error) {
                polyplus$createShader = null;
                polyplus$activeProgram = null;
                LobbySodiumWave.fail("Sodium's ShaderChunkRenderer has no createShader(String, ChunkShaderOptions) or activeProgram", error);
            }
        }
        return polyplus$createShader != null;
    }

    @Inject(method = "compileProgram(Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderOptions;)Lnet/caffeinemc/mods/sodium/client/gl/shader/GlProgram;", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$lobbyProgram(ChunkShaderOptions options, CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> cir) {
        if (!LobbySodiumWave.active()) {
            return;
        }
        GlProgram<ChunkShaderInterface> program = this.polyplus$lobbyPrograms.get(options);
        if (program == null) {
            program = this.polyplus$buildLobbyProgram(options);
        }
        if (program != null) {
            cir.setReturnValue(program);
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private GlProgram<ChunkShaderInterface> polyplus$buildLobbyProgram(ChunkShaderOptions options) {
        if (!polyplus$resolve()) {
            return null;
        }
        GlProgram<ChunkShaderInterface> program;
        GlUniformFloat2v wave;
        try {
            Object built = polyplus$createShader.invoke((Object) this, polyplus$LOBBY_PATH, options);
            program = (GlProgram<ChunkShaderInterface>) built;
            wave = program.bindUniformOptional(polyplus$WAVE_UNIFORM, GlUniformFloat2v::new);
        } catch (Throwable error) {
            LobbySodiumWave.fail("building the lobby chunk program", error);
            return null;
        }
        if (wave == null || !LobbySodiumWave.active()) {
            program.delete();
            if (wave == null) {
                LobbySodiumWave.fail("the lobby chunk program has no " + polyplus$WAVE_UNIFORM, null);
            }
            return null;
        }
        this.polyplus$lobbyPrograms.put(options, program);
        this.polyplus$waveUniforms.put(program, wave);
        polyplus$LOGGER.info("Built Sodium lobby chunk program for pass {}", options.pass());
        return program;
    }

    @Inject(method = "begin", at = @At("TAIL"), remap = false, require = 0, expect = 0)
    private void polyplus$lobbyWave(CallbackInfo ci) {
        if (this.polyplus$waveUniforms.isEmpty() || polyplus$activeProgram == null) {
            return;
        }
        Object active;
        try {
            active = polyplus$activeProgram.invoke((Object) this);
        } catch (Throwable error) {
            return;
        }
        GlUniformFloat2v wave = this.polyplus$waveUniforms.get(active);
        if (wave != null) {
            wave.set(LobbySodiumWave.seconds(), LobbySodiumWave.aim());
        }
    }

    @Inject(method = "delete", at = @At("TAIL"), remap = false, require = 0, expect = 0)
    private void polyplus$deleteLobbyPrograms(CallbackInfo ci) {
        this.polyplus$lobbyPrograms.values().forEach(GlProgram::delete);
        this.polyplus$lobbyPrograms.clear();
        this.polyplus$waveUniforms.clear();
    }
    *///?}
}
