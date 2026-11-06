package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if >= 1.21.4 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}

@Mixin(CapeLayer.class)
public abstract class MixinCapeLayer {
    //? if >= 1.21.10 {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V", at = @At("HEAD"))
    private void polyplus$followRoot(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/PlayerRenderState;FF)V", at = @At("HEAD"))
    private void polyplus$followRoot(PoseStack poseStack, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot, CallbackInfo ci) {
    *///?}
        poseStack.pushPose();
        ((RenderLayer<?, ?>) (Object) this).getParentModel().root().translateAndRotate(poseStack);
    }

    //? if >= 1.21.10 {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V", at = @At("RETURN"))
    private void polyplus$leaveRoot(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/PlayerRenderState;FF)V", at = @At("RETURN"))
    private void polyplus$leaveRoot(PoseStack poseStack, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot, CallbackInfo ci) {
    *///?}
        poseStack.popPose();
    }
}
//?}
