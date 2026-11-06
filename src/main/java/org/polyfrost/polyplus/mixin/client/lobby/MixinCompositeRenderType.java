package org.polyfrost.polyplus.mixin.client.lobby;

//? if < 1.21.11 && >= 1.21.5 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.polyfrost.polyplus.client.lobby.LobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public class MixinCompositeRenderType {
    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private RenderPipeline polyplus$lightBothFaces(RenderPipeline original) {
        //? if < 1.21.10 {
        return LobbyPipelines.hologram(LobbyPipelines.entity(original));
        //?} else {
        /^return LobbyPipelines.hologram(original);
        ^///?}
    }
}
*///?}
