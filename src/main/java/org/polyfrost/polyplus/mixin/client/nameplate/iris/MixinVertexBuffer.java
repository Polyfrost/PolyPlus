package org.polyfrost.polyplus.mixin.client.nameplate.iris;

//? if = 1.21.1 {
/*import com.mojang.blaze3d.vertex.VertexBuffer;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIrisTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif = 1.21.4 {
/*import com.mojang.blaze3d.vertex.VertexBuffer;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.renderer.CompiledShaderProgram;
import org.joml.Matrix4f;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIrisTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

//? if = 1.21.1 || = 1.21.4 {
/*@IfModLoaded("iris")
@Mixin(VertexBuffer.class)
*///?}
public class MixinVertexBuffer {
    //? if = 1.21.1 {
    /*@Inject(method = "_drawWithShader", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void polyplus$drawIntoShaderPack(Matrix4f modelView, Matrix4f projection, ShaderInstance shader, CallbackInfo ci) {
        if (NameplateIrisTarget.ownsName(shader.getName())) {
            NameplateIrisTarget.beforeDraw();
        }
    }

    @Inject(method = "_drawWithShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"))
    private void polyplus$restoreShaderPackTarget(Matrix4f modelView, Matrix4f projection, ShaderInstance shader, CallbackInfo ci) {
        NameplateIrisTarget.afterDraw();
    }
    *///?} elif = 1.21.4 {
    /*@Inject(method = "drawWithShader", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void polyplus$drawIntoShaderPack(Matrix4f modelView, Matrix4f projection, CompiledShaderProgram shader, CallbackInfo ci) {
        if (NameplateIrisTarget.owns(shader)) {
            NameplateIrisTarget.beforeDraw();
        }
    }

    @Inject(method = "drawWithShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CompiledShaderProgram;clear()V"))
    private void polyplus$restoreShaderPackTarget(Matrix4f modelView, Matrix4f projection, CompiledShaderProgram shader, CallbackInfo ci) {
        NameplateIrisTarget.afterDraw();
    }
    *///?}
}
