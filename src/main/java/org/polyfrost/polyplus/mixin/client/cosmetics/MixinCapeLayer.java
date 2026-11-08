package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if >= 1.21.4 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.polyfrost.polyplus.compat.EntityModelFeaturesCompat;
import org.spongepowered.asm.mixin.Mixin;

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
    @WrapMethod(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V")
    private void polyplus$followRoot(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, Operation<Void> original) {
    //?} else {
    /*@WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/PlayerRenderState;FF)V")
    private void polyplus$followRoot(PoseStack poseStack, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot, Operation<Void> original) {
    *///?}
        poseStack.pushPose();
        try {
            if (!EntityModelFeaturesCompat.animatesRoot(((RenderLayer<?, ?>) (Object) this).getParentModel())) {
                ((RenderLayer<?, ?>) (Object) this).getParentModel().root().translateAndRotate(poseStack);
            }
            //? if >= 1.21.10 {
            original.call(poseStack, collector, light, state, yRot, xRot);
            //?} else {
            /*original.call(poseStack, buffers, light, state, yRot, xRot);
            *///?}
        } finally {
            poseStack.popPose();
        }
    }
}
//?}
