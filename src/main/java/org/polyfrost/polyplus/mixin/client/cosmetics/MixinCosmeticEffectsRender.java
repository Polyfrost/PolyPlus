package org.polyfrost.polyplus.mixin.client.cosmetics;

import org.polyfrost.polyplus.client.cosmetics.render.CosmeticEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >= 26.1 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} elif >= 1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
*///?} elif >= 1.21.4 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticPrepass;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticView;

import java.util.List;
*///?} elif = 1.21.1 {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticPrepass;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticView;
*///?} else {
/*import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticPrepass;
*///?}

//? if > 1.8.9 {
@Mixin(LevelRenderer.class)
//?} else {
/*@Mixin(WorldRenderer.class)
*///?}
public class MixinCosmeticEffectsRender {
    //? if >= 1.21.10 {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void polyplus$submitCosmeticEffects(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        CosmeticEffects.submit(poseStack, collector);
    }
    //?} elif >= 1.21.4 {
    /*@WrapOperation(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;collectVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)Z")
    )
    private boolean polyplus$collectCosmetics(LevelRenderer renderer, Camera camera, Frustum frustum, List<Entity> entities, Operation<Boolean> original, @Local(argsOnly = true) DeltaTracker deltaTracker) {
        boolean visible = original.call(renderer, camera, frustum, entities);
        CosmeticPrepass.collect(camera, deltaTracker, entities);
        return visible;
    }
    *///?} elif = 1.21.1 {
    /*@Inject(
        method = "renderLevel",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;captureFrustum:Z", opcode = Opcodes.GETFIELD, ordinal = 0)
    )
    private void polyplus$collectCosmetics(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci,
        @Local Frustum frustum
    ) {
        CosmeticPrepass.collect(camera, deltaTracker, frustum);
    }
    *///?} else {
    /*@Inject(
        method = "renderEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;enableLightMap()V", ordinal = 0)
    )
    private void polyplus$collectCosmetics(Entity camera, Culler culler, float tickDelta, CallbackInfo ci) {
        CosmeticPrepass.collect(camera, culler, tickDelta);
    }
    *///?}

    //? if > 1.8.9 && < 1.21.10 {
    /*@Inject(method = "renderEntity", at = @At("HEAD"))
    private void polyplus$enterEntityView(Entity entity, double cameraX, double cameraY, double cameraZ, float partialTick, PoseStack poseStack, MultiBufferSource buffers, CallbackInfo ci) {
        CosmeticView.enterEntity(poseStack.last().pose());
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void polyplus$exitEntityView(Entity entity, double cameraX, double cameraY, double cameraZ, float partialTick, PoseStack poseStack, MultiBufferSource buffers, CallbackInfo ci) {
        CosmeticView.exitEntity();
    }
    *///?}

    //? if >= 1.21.4 && < 1.21.10 {
    /*@Inject(method = "renderEntities", at = @At("TAIL"))
    private void polyplus$renderCosmeticEffects(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Camera camera, DeltaTracker deltaTracker, List<Entity> entities, CallbackInfo ci) {
        CosmeticEffects.render(poseStack, buffers);
    }
    *///?} elif = 1.21.1 {
    /*@Inject(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", ordinal = 0)
    )
    private void polyplus$renderCosmeticEffects(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        CosmeticEffects.render();
    }
    *///?} elif = 1.8.9 {
    /*@Inject(method = "renderEntities", at = @At("TAIL"))
    private void polyplus$renderCosmeticEffects(Entity camera, Culler culler, float tickDelta, CallbackInfo ci) {
        CosmeticEffects.render();
    }
    *///?}
}
