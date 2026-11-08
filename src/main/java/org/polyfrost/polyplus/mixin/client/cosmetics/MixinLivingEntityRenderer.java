package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if = 1.21.1 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseAvatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @Shadow
    protected EntityModel<?> model;

    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void polyplus$posePauseAvatar(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, Operation<Void> original) {
        float[] kept = PauseAvatar.beginLegacy(entity);
        try {
            original.call(entity, yaw, partialTicks, poseStack, buffers, light);
        } finally {
            if (kept != null) PauseAvatar.endLegacy(entity, kept);
        }
    }

    @ModifyReturnValue(method = "getBob", at = @At("RETURN"))
    private float polyplus$breathePauseAvatar(float original, LivingEntity entity) {
        return PauseAvatar.legacyAge(entity, original);
    }

    @ModifyReturnValue(method = "getAttackAnim", at = @At("RETURN"))
    private float polyplus$settleAttack(float original, LivingEntity entity) {
        return PauseAvatar.legacyFade(entity, original);
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/WalkAnimationState;speed(F)F"))
    private float polyplus$settleWalk(float original, @Local(argsOnly = true) LivingEntity entity) {
        return PauseAvatar.legacyFade(entity, original);
    }

    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
            shift = At.Shift.AFTER
        )
    )
    private void polyplus$applyEmoteRoot(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (model instanceof PlayerModel<?>) {
            ((PlayerModelRootAccess) model).polyplus$root().translateAndRotate(poseStack);
        }
    }
}
*///?} elif = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.entity.living.LivingEntity;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseAvatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @Shadow
    protected Model model;

    @WrapMethod(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V")
    private void polyplus$posePauseAvatar(LivingEntity entity, double x, double y, double z, float yaw, float tickDelta, Operation<Void> original) {
        float[] kept = PauseAvatar.beginLegacy(entity);
        try {
            original.call(entity, x, y, z, yaw, tickDelta);
        } finally {
            if (kept != null) PauseAvatar.endLegacy(entity, kept);
        }
    }

    @ModifyReturnValue(method = "getBob", at = @At("RETURN"))
    private float polyplus$breathePauseAvatar(float original, LivingEntity entity) {
        return PauseAvatar.legacyAge(entity, original);
    }

    @ModifyReturnValue(method = "getAttackAnimationProgress", at = @At("RETURN"))
    private float polyplus$settleAttack(float original, LivingEntity entity) {
        return PauseAvatar.legacyFade(entity, original);
    }

    @Inject(
        method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/model/Model;setupAnimation(FFFFFFLnet/minecraft/entity/Entity;)V",
            shift = At.Shift.AFTER
        )
    )
    private void polyplus$applyEmoteRoot(LivingEntity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
        if (model instanceof PlayerModel) {
            ((PlayerModelRootAccess) model).polyplus$root().transform(0.0625F);
        }
    }
}
*///?}
