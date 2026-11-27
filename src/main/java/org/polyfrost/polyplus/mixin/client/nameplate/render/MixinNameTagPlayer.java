package org.polyfrost.polyplus.mixin.client.nameplate.render;

import org.polyfrost.polyplus.client.nameplate.render.NameTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} elif >= 1.21.4 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
*///?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.entity.Entity;
*///?}
//? if >= 1.21.10 && < 26.1 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
*///?}

//? if >= 1.21.10 {
@Mixin(AvatarRenderer.class)
//?} else {
/*@Mixin(PlayerRenderer.class)
*///?}
public class MixinNameTagPlayer {
    //? if >= 1.21.10 {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("RETURN")
    )
    private void polyplus$bindNameTag(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        NameTags.bind(entity, state);
    }

    //? if < 26.1 {
    /*@WrapOperation(
        method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/CameraRenderState;)V",
            ordinal = 1
        )
    )
    private void polyplus$wrapNameTag(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        Vec3 attachment,
        int offset,
        Component name,
        boolean seeThrough,
        int lightCoords,
        double distanceToCameraSq,
        CameraRenderState camera,
        Operation<Void> original,
        @Local(argsOnly = true) AvatarRenderState state
    ) {
        Object handle = NameTags.beginName(state, collector);
        boolean completed = false;
        try {
            original.call(collector, poseStack, attachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera);
            completed = true;
        } finally {
            NameTags.endName(handle, collector, completed);
        }
    }
    *///?}
    //?} elif >= 1.21.4 {
    /*@Inject(
        method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
        at = @At("RETURN")
    )
    private void polyplus$bindNameTag(AbstractClientPlayer player, PlayerRenderState state, float partialTicks, CallbackInfo ci) {
        NameTags.bind(player, state);
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            ordinal = 0
        )
    )
    private void polyplus$wrapScoreTag(
        PlayerRenderer renderer,
        EntityRenderState state,
        Component score,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int lightCoords,
        Operation<Void> original
    ) {
        NameTags.renderStateScore(state, () -> original.call(renderer, state, score, poseStack, buffers, lightCoords));
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            ordinal = 1
        )
    )
    private void polyplus$wrapNameTag(
        PlayerRenderer renderer,
        EntityRenderState state,
        Component name,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int lightCoords,
        Operation<Void> original
    ) {
        boolean started = NameTags.beginStateName(state);
        try {
            original.call(renderer, state, name, poseStack, buffers, lightCoords);
        } finally {
            NameTags.endName(started);
        }
    }
    *///?} elif > 1.8.9 {
    /*@WrapOperation(
        method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            ordinal = 0
        )
    )
    private void polyplus$wrapScoreTag(
        PlayerRenderer renderer,
        Entity entity,
        Component score,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int lightCoords,
        float partialTick,
        Operation<Void> original
    ) {
        NameTags.renderEntityScore(entity, () -> original.call(renderer, entity, score, poseStack, buffers, lightCoords, partialTick));
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            ordinal = 1
        )
    )
    private void polyplus$wrapNameTag(
        PlayerRenderer renderer,
        Entity entity,
        Component name,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int lightCoords,
        float partialTick,
        Operation<Void> original
    ) {
        boolean started = NameTags.beginEntityName(entity);
        try {
            original.call(renderer, entity, name, poseStack, buffers, lightCoords, partialTick);
        } finally {
            NameTags.endName(started);
        }
    }
    *///?} else {
    /*@WrapOperation(
        method = "renderNameTag(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDLjava/lang/String;FD)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;renderNameTag(Lnet/minecraft/entity/Entity;DDDLjava/lang/String;FD)V"
        )
    )
    private void polyplus$wrapNameLine(
        PlayerRenderer renderer,
        Entity entity,
        double x,
        double y,
        double z,
        String name,
        float scale,
        double distance,
        Operation<Void> original
    ) {
        Entity previous = NameTags.enterNameLine(entity);
        try {
            original.call(renderer, entity, x, y, z, name, scale, distance);
        } finally {
            NameTags.leaveNameLine(previous);
        }
    }
    *///?}
}
