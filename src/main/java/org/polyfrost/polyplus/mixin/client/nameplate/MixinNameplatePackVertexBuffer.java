package org.polyfrost.polyplus.mixin.client.nameplate;

//? if > 1.8.9 && < 1.21.5 {
/*import com.mojang.blaze3d.vertex.VertexBuffer;
import org.joml.Matrix4f;
import org.polyfrost.polyplus.client.nameplate.material.NameplateShaderPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >= 1.21.4 {
import net.minecraft.client.renderer.CompiledShaderProgram;
//?} else {
/^import net.minecraft.client.renderer.ShaderInstance;
^///?}

@Mixin(VertexBuffer.class)
public class MixinNameplatePackVertexBuffer {
    //? if >= 1.21.4 {
    @Inject(
        method = "drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/CompiledShaderProgram;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V")
    )
    private void polyplus$drawIntoShaderPack(Matrix4f modelView, Matrix4f projection, CompiledShaderProgram shader, CallbackInfo ci) {
        NameplateShaderPacks.redirect(shader);
    }

    @Inject(
        method = "drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/CompiledShaderProgram;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CompiledShaderProgram;clear()V")
    )
    private void polyplus$restoreAfterDraw(Matrix4f modelView, Matrix4f projection, CompiledShaderProgram shader, CallbackInfo ci) {
        NameplateShaderPacks.restore();
    }
    //?} else {
    /^@Inject(
        method = "_drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V")
    )
    private void polyplus$drawIntoShaderPack(Matrix4f modelView, Matrix4f projection, ShaderInstance shader, CallbackInfo ci) {
        NameplateShaderPacks.redirect(shader);
    }

    @Inject(
        method = "_drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V")
    )
    private void polyplus$restoreAfterDraw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader, CallbackInfo ci) {
        NameplateShaderPacks.restore();
    }
    ^///?}
}
*///?}
