package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateShaderSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

//? if >= 26.3 {
@Mixin(ShaderManager.Configs.class)
//?}
public class MixinShaderManagerConfigs {
    //? if >= 26.3 {
    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void polyplus$serveNameplateShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        String source = NameplateShaderSources.shader(id, type);
        if (source != null) {
            cir.setReturnValue(source);
        }
    }

    @Inject(method = "getInclude", at = @At("HEAD"), cancellable = true)
    private void polyplus$serveNameplateInclude(Identifier id, CallbackInfoReturnable<ShaderSource.CachedIncludeSource> cir) {
        ShaderSource.CachedIncludeSource include = NameplateShaderSources.include(id);
        if (include != null) {
            cir.setReturnValue(include);
        }
    }
    //?}
}
