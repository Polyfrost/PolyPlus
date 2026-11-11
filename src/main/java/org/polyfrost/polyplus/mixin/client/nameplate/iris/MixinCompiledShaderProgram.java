package org.polyfrost.polyplus.mixin.client.nameplate.iris;

//? if = 1.21.4 {
/*import com.mojang.blaze3d.shaders.CompiledShader;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.renderer.CompiledShaderProgram;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIrisTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

//? if = 1.21.4 {
/*@IfModLoaded("iris")
@Mixin(CompiledShaderProgram.class)
*///?}
public class MixinCompiledShaderProgram {
    //? if = 1.21.4 {
    /*@Inject(method = "link", at = @At("RETURN"))
    private static void polyplus$trackNameplateProgram(CompiledShader vertex, CompiledShader fragment, VertexFormat format, CallbackInfoReturnable<CompiledShaderProgram> cir) {
        NameplateIrisTarget.track(cir.getReturnValue(), vertex.getId());
    }
    *///?}
}
