package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} elif = 26.2 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialUniforms;
import org.polyfrost.polyplus.client.nameplate.render.material.RenderTypeNameAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
*///?}

//? if >= 26.2 {
@Mixin(PreparedRenderType.class)
//?}
public class MixinPreparedRenderType
    //? if = 26.2
    //implements RenderTypeNameAccess
{
    //? if >= 26.3 {
    @Inject(
        method = "draw",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms(Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            shift = At.Shift.AFTER
        )
    )
    private void polyplus$bindNameplateMaterial(
        StagedVertexBuffer.ExecuteInfo info,
        RenderPass renderPass,
        RenderPipeline renderPipeline,
        CallbackInfo ci
    ) {
        NameplateMaterialUniforms.bind(((PreparedRenderType) (Object) this).name(), renderPass);
    }
    //?} elif = 26.2 {
    /*@Unique
    private String polyplus$renderTypeName;

    @Override
    public String polyplus$renderTypeName() {
        return polyplus$renderTypeName;
    }

    @Override
    public void polyplus$setRenderTypeName(String name) {
        polyplus$renderTypeName = name;
    }

    @WrapOperation(
        method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms(Lcom/mojang/blaze3d/systems/RenderPass;)V"
        )
    )
    private void polyplus$bindNameplateMaterial(RenderPass renderPass, Operation<Void> original) {
        original.call(renderPass);
        if (polyplus$renderTypeName != null) {
            NameplateMaterialUniforms.bind(polyplus$renderTypeName, renderPass);
        }
    }
    *///?}
}
