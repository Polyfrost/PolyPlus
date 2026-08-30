package org.polyfrost.polyplus.mixin.compat.waveycapes;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "dev.tr7zw.waveycapes.render.CustomCapeRenderer", remap = false)
public class MixinCustomCapeRenderer {
    //? if >= 1.21.10 {
    @WrapMethod(method = "modifyPoseStack", remap = false, require = 0, expect = 0)
    private void polyplus$staticCapeForPreview(PoseStack poseStack, @Coerce Object capeRenderInfo, float delta, int part, Operation<Void> original) {
        if (!PlayerPreviewRenderer.isRenderingPreview()) {
            original.call(poseStack, capeRenderInfo, delta, part);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 0.125D);
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(6.0)));
        poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(180.0)));
    }
    //?}
}
