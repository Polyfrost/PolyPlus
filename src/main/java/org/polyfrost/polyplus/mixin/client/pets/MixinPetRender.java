package org.polyfrost.polyplus.mixin.client.pets;

import org.polyfrost.polyplus.client.pets.PetRenderHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} elif >= 1.21.4 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
*///?} elif > 1.8.9 {
/*import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Shadow;
*///?} else {
/*import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Shadow;
*///?}

//? if >= 26.1 {
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} elif >= 1.21.10 {
/*import net.minecraft.client.renderer.state.LevelRenderState;
*///?}

//? if > 1.8.9 {
@Mixin(LevelRenderer.class)
//?} else {
/*@Mixin(WorldRenderer.class)
*///?}
public class MixinPetRender {
    //? if >= 1.21.10 {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void polyplus$submitPets(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        PetRenderHook.submit(poseStack, collector);
    }
    //?} elif >= 1.21.4 {
    /*@Shadow
    private ClientLevel level;

    @Inject(method = "collectVisibleEntities", at = @At("HEAD"))
    private void polyplus$capturePetFrustum(Camera camera, Frustum frustum, List<Entity> entities, CallbackInfoReturnable<Boolean> cir) {
        PetRenderHook.capture(frustum);
    }

    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void polyplus$renderPets(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Camera camera, DeltaTracker deltaTracker, List<Entity> entities, CallbackInfo ci) {
        PetRenderHook.render(this.level, camera, deltaTracker, poseStack, buffers);
    }
    *///?} elif > 1.8.9 {
    /*@Shadow
    private ClientLevel level;

    @Shadow
    private Frustum cullingFrustum;

    @Inject(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", ordinal = 0)
    )
    private void polyplus$renderPets(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        PetRenderHook.render(this.level, camera, this.cullingFrustum, deltaTracker);
    }
    *///?} else {
    /*@Shadow
    private ClientWorld world;

    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void polyplus$renderPets(Entity camera, Culler culler, float tickDelta, CallbackInfo ci) {
        PetRenderHook.render(this.world, culler, tickDelta);
    }
    *///?}
}
