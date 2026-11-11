package org.polyfrost.polyplus.mixin.client.pause;

import org.polyfrost.polyplus.client.gui.pausescreen.PauseStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
//?} else {
/*import java.nio.FloatBuffer;
import net.minecraft.client.render.GameRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

//? if > 1.8.9 && < 26.1 {
/*import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

@Mixin(GameRenderer.class)
public class MixinPauseRenderer {
    //? if > 1.8.9 {
    @Unique
    private static final Matrix4f polyplus$bobPose = new Matrix4f();

    @Unique
    private static final Matrix3f polyplus$bobNormal = new Matrix3f();

    @Inject(method = "render", at = @At("HEAD"))
    private void polyplus$stepPauseStage(CallbackInfo ci) {
        PauseStage.frame();
    }

    @Inject(method = {"bobHurt", "bobView"}, at = @At("HEAD"))
    private void polyplus$keepPauseBob(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        polyplus$bobPose.set(poseStack.last().pose());
        polyplus$bobNormal.set(poseStack.last().normal());
    }

    @Inject(method = {"bobHurt", "bobView"}, at = @At("RETURN"))
    private void polyplus$fadePauseBob(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        float keep = PauseStage.bobKeep();
        if (keep >= 1.0F) return;
        PoseStack.Pose last = poseStack.last();
        polyplus$bobPose.lerp(last.pose(), keep, last.pose());
        polyplus$bobNormal.lerp(last.normal(), keep, last.normal());
    }
    //?} else {
    /*@Unique
    private static final FloatBuffer polyplus$bobBefore = BufferUtils.createFloatBuffer(16);

    @Unique
    private static final FloatBuffer polyplus$bobAfter = BufferUtils.createFloatBuffer(16);

    @Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void polyplus$stepPauseStage(float tickDelta, long startTime, CallbackInfo ci) {
        PauseStage.frame();
    }

    @Inject(method = {"applyHurtCam", "applyViewBobbing"}, at = @At("HEAD"))
    private void polyplus$keepPauseBob(float tickDelta, CallbackInfo ci) {
        if (PauseStage.bobKeep() >= 1.0F) return;
        polyplus$bobBefore.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, polyplus$bobBefore);
    }

    @Inject(method = {"applyHurtCam", "applyViewBobbing"}, at = @At("RETURN"))
    private void polyplus$fadePauseBob(float tickDelta, CallbackInfo ci) {
        float keep = PauseStage.bobKeep();
        if (keep >= 1.0F) return;
        polyplus$bobAfter.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, polyplus$bobAfter);
        for (int i = 0; i < 16; i++) {
            float before = polyplus$bobBefore.get(i);
            polyplus$bobAfter.put(i, before + (polyplus$bobAfter.get(i) - before) * keep);
        }
        polyplus$bobAfter.rewind();
        GL11.glLoadMatrix(polyplus$bobAfter);
    }

    @Inject(method = "getFov", at = @At("HEAD"))
    private void polyplus$pauseFovPass(float tickDelta, boolean fovChanged, CallbackInfoReturnable<Float> cir) {
        PauseStage.fovPass(fovChanged);
    }

    @Inject(method = "transformCamera", at = @At("HEAD"), cancellable = true)
    private void polyplus$pauseCamera(float tickDelta, CallbackInfo ci) {
        if (PauseStage.applyLegacyCamera(tickDelta)) ci.cancel();
    }
    *///?}

    //? if > 1.8.9 && < 26.1 {
    /*@Inject(method = "getFov", at = @At("HEAD"))
    private void polyplus$pauseFovPass(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<?> cir) {
        PauseStage.fovPass(useFovSetting);
    }
    *///?}
}
