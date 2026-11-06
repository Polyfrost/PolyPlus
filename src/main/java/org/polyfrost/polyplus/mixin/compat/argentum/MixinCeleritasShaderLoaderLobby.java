package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.polyfrost.polyplus.client.lobby.LobbySodiumWave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.shader.ShaderLoader", remap = false)
public class MixinCeleritasShaderLoaderLobby {
    @Unique
    private static final String POLYPLUS_LOBBY_PREFIX = "sodium:polyplus_lobby/";

    @Inject(method = "getShaderSource(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$lobbySource(String name, CallbackInfoReturnable<String> cir) {
        if (!name.startsWith(POLYPLUS_LOBBY_PREFIX)) return;
        String source = ShaderLoader.getShaderSource("sodium:" + name.substring(POLYPLUS_LOBBY_PREFIX.length()));
        String patched = name.endsWith(".vsh")
                ? LobbySodiumWave.patchVertex(source, LobbySodiumWave.Flavour.CELERITAS)
                : LobbySodiumWave.patchFragment(source, LobbySodiumWave.Flavour.CELERITAS);
        cir.setReturnValue(patched != null ? patched : source);
    }
}
*///?}
