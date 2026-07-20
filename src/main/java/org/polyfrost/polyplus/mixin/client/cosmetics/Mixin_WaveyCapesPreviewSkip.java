//? if >= 1.21.10 {
package org.polyfrost.polyplus.mixin.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.tr7zw.waveycapes.render.CustomCapeRenderer", remap = false)
public class Mixin_WaveyCapesPreviewSkip {
    @Inject(method = "modifyPoseStack", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$staticCapeForPreview(PoseStack poseStack, @Coerce Object capeRenderInfo, float delta, int part, CallbackInfo ci) {
        if (!PlayerPreviewRenderer.isRenderingPreview()) return;
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 0.125D);
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(6.0)));
        poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(180.0)));
        ci.cancel();
    }
}
//?}
