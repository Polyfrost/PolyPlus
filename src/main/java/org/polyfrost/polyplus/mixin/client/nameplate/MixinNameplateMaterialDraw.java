package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.5 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.polyfrost.polyplus.client.nameplate.material.NameplateMaterialBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >= 26.3 {
import com.mojang.renderpearl.api.commands.RenderPass;
//?} else {
/*import com.mojang.blaze3d.systems.RenderPass;
*///?}
//? if >= 26.2 {
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
//?}

//? if >= 26.2 {
@Mixin(PreparedRenderType.class)
//?} elif >= 1.21.11 {
/*@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderType")
*///?} else {
/*@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
*///?}
public class MixinNameplateMaterialDraw {
    //? if >= 26.3 {
    @WrapOperation(
        method = "draw(Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V")
    )
    private void polyplus$bindNameplateMaterial(RenderPass pass, int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance, Operation<Void> original) {
        NameplateMaterialBackend.bind(((PreparedRenderType) (Object) this).name(), pass);
        original.call(pass, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }
    //?} elif >= 26.2 {
    /*@WrapOperation(
        method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIIII)V")
    )
    private void polyplus$bindNameplateMaterial(RenderPass pass, int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance, Operation<Void> original) {
        NameplateMaterialBackend.bindPrepared((PreparedRenderType) (Object) this, pass);
        original.call(pass, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }
    *///?} elif >= 1.21.6 {
    /*@WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"))
    private void polyplus$bindNameplateMaterial(RenderPass pass, int baseVertex, int firstIndex, int indexCount, int instanceCount, Operation<Void> original) {
        NameplateMaterialBackend.bind(this, pass);
        original.call(pass, baseVertex, firstIndex, indexCount, instanceCount);
    }
    *///?} else {
    /*@WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(II)V"))
    private void polyplus$bindNameplateMaterial(RenderPass pass, int firstIndex, int indexCount, Operation<Void> original) {
        NameplateMaterialBackend.bind(this, pass);
        original.call(pass, firstIndex, indexCount);
    }
    *///?}
}
//?}
