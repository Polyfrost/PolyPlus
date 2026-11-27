package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import org.polyfrost.polyplus.client.nameplate.material.NameplateShaderPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlRenderPipeline.class)
public class MixinNameplatePackPipeline {
    @Inject(method = "bind", at = @At("HEAD"))
    private void polyplus$unlockShaderPackBlend(CallbackInfo ci) {
        NameplateShaderPacks.unlockBlend(this);
    }
}
//?}
