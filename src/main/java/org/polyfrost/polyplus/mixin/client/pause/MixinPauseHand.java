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
//?} else {
/*import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.platform.GlStateManager;
*///?}

//? if >= 26.3 {
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.ItemInHandRenderer;
*///?}

//? if >= 26.3 {
@Mixin(FirstPersonHandsAndItemsRenderer.class)
//?} else {
/*@Mixin(ItemInHandRenderer.class)
*///?}
public class MixinPauseHand {
    @Unique
    private static final float POLYPLUS_SINK = 0.7F;

    //? if >= 26.2 {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void polyplus$sinkHand(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        poseStack.translate(0.0F, -PauseStage.handSink() * POLYPLUS_SINK, 0.0F);
    }
    //?} elif > 1.8.9 {
    /*@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void polyplus$sinkHand(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        poseStack.translate(0.0F, -PauseStage.handSink() * POLYPLUS_SINK, 0.0F);
    }
    *///?} else {
    /*@Inject(method = "renderInFirstPerson", at = @At("HEAD"))
    private void polyplus$sinkHand(float tickDelta, CallbackInfo ci) {
        GlStateManager.translatef(0.0F, -PauseStage.handSink() * POLYPLUS_SINK, 0.0F);
    }
    *///?}
}
