package org.polyfrost.polyplus.mixin.client.lobby;

//? if >= 26.2 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//? if >= 26.3 {
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
//?} else {
/*import com.mojang.blaze3d.shaders.ShaderType;
import org.spongepowered.asm.mixin.Shadow;
*///?}
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.lobby.LobbySodiumUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >= 26.3 {
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$Configs")
public abstract class MixinShaderConfigsLobby {
    @ModifyReturnValue(method = "getShader", at = @At("RETURN"))
    private String polyplus$sodiumLobby(String original, Identifier id, ShaderType type) {
        if (original != null) return original;
        Identifier sodium = LobbySodiumUniforms.sodiumId(id);
        return sodium == null ? null : LobbySodiumUniforms.patch(((ShaderSource) (Object) this).getShader(sodium, type), type);
    }
}
//?} else {
/*@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
public abstract class MixinShaderConfigsLobby {
    @Shadow
    public abstract String getShaderSource(Identifier id, ShaderType type);

    @ModifyReturnValue(method = "getShaderSource", at = @At("RETURN"))
    private String polyplus$sodiumLobby(String original, Identifier id, ShaderType type) {
        if (original != null) return original;
        Identifier sodium = LobbySodiumUniforms.sodiumId(id);
        return sodium == null ? null : LobbySodiumUniforms.patch(this.getShaderSource(sodium, type), type);
    }
}
*///?}
//?}
