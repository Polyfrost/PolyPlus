package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.polyfrost.polyplus.client.nameplate.render.NameplateExtraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
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
import org.polyfrost.polyplus.client.nameplate.render.NameplateBufferAdapter;
import org.polyfrost.polyplus.client.nameplate.render.NameplateExtraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.polyfrost.polyplus.client.nameplate.render.NameplateBufferAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
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
import org.polyfrost.polyplus.client.nameplate.render.NameplateSubmitAdapter;
*///?}

//? if >= 1.21.10 {
@Mixin(AvatarRenderer.class)
//?} elif > 1.8.9 {
/*@Mixin(PlayerRenderer.class)
*///?}
public class MixinAvatarRenderer {
    //? if >= 1.21.10 {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("RETURN")
    )
    private void polyplus$bindNameplate(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        NameplateExtraction.bindAvatar(entity, state);
    }
    //?} elif >= 1.21.4 {
    /*@Inject(
        method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
        at = @At("RETURN")
    )
    private void polyplus$bindNameplate(AbstractClientPlayer entity, PlayerRenderState state, float partialTicks, CallbackInfo ci) {
        NameplateExtraction.bindPlayer(entity, state);
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
        NameplateBufferAdapter.renderName(state, name, poseStack, buffers, restyled -> original.call(renderer, state, restyled, poseStack, buffers, lightCoords));
    }
    *///?} elif > 1.8.9 {
    /*@WrapOperation(
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
        Operation<Void> original,
        @Local(argsOnly = true) AbstractClientPlayer player
    ) {
        NameplateBufferAdapter.renderName(
            player,
            name,
            restyled -> original.call(renderer, entity, restyled, poseStack, buffers, lightCoords, partialTick)
        );
    }
    *///?}

    //? if >= 1.21.10 && < 26.1 {
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
        NameplateSubmitAdapter.submitName(state, collector, poseStack, attachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera, original);
    }
    *///?}
}
