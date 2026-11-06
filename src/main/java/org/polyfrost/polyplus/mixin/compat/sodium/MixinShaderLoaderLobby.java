//~ identifier
package org.polyfrost.polyplus.mixin.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if sodium && < 26.2 {
/*import java.util.regex.Pattern;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.lobby.LobbySodiumWave;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class MixinShaderLoaderLobby {
    //? if sodium && < 26.2 {
    /*@Unique
    private static final String polyplus$REQUEST = "polyplus_lobby/";
    @Unique
    private static final String polyplus$NAMESPACE = "polyplus";
    @Unique
    private static final String polyplus$LOBBY = "sodium_lobby/";
    @Unique
    private static final Pattern polyplus$CHUNK_FADE = Pattern.compile("\\bfadeFactor\\s*=\\s*([^;]*);");

    @ModifyVariable(method = "loadShader", at = @At("HEAD"), argsOnly = true, remap = false, require = 0, expect = 0)
    private static Identifier polyplus$lobbyShaderId(Identifier name) {
        if (!"sodium".equals(name.getNamespace()) || !name.getPath().startsWith(polyplus$REQUEST)) {
            return name;
        }
        return Identifier.fromNamespaceAndPath(polyplus$NAMESPACE, polyplus$LOBBY + name.getPath().substring(polyplus$REQUEST.length()));
    }

    @Inject(method = "getShaderSource", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$lobbyShaderSource(Identifier name, CallbackInfoReturnable<String> cir) {
        if (!polyplus$NAMESPACE.equals(name.getNamespace()) || !name.getPath().startsWith(polyplus$LOBBY)) {
            return;
        }
        String file = name.getPath().substring(polyplus$LOBBY.length());
        String source = ShaderLoader.getShaderSource(Identifier.fromNamespaceAndPath("sodium", "blocks/" + file));
        String patched;
        if (file.endsWith(".vsh")) {
            patched = LobbySodiumWave.patchVertex(source, LobbySodiumWave.Flavour.GL);
            if (patched != null) {
                patched = polyplus$CHUNK_FADE.matcher(patched).replaceAll("fadeFactor = u_PolyLobbyWave.x >= 0.0 ? 1.0 : ($1);");
            }
        } else {
            patched = LobbySodiumWave.patchFragment(source, LobbySodiumWave.Flavour.GL);
        }
        cir.setReturnValue(patched != null ? patched : source);
    }
    *///?}
}
