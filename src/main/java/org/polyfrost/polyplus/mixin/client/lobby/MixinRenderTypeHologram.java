package org.polyfrost.polyplus.mixin.client.lobby;

//? if >= 1.21.11 {
//? if >= 26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}
//? if >= 26.2 {
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
//?} else {
/*import net.minecraft.client.renderer.rendertype.RenderType;
*///?}
import org.polyfrost.polyplus.client.lobby.LobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

//? if >= 26.2 {
@Mixin(PreparedRenderType.class)
//?} else {
/*@Mixin(RenderType.class)
*///?}
public class MixinRenderTypeHologram {
    //? if >= 26.3 {
    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getCompiledPipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;"))
    //?} elif >= 26.2 {
    /*@ModifyArg(method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    *///?} else {
    /*@ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    *///?}
    private RenderPipeline polyplus$hologram(RenderPipeline original) {
        return LobbyPipelines.hologram(original);
    }
}
//?}
