package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.polyplus.client.nameplate.NameplateSelfVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.polyplus.client.nameplate.NameplateSelfVisibility;
import org.polyfrost.polyplus.client.nameplate.render.NameplateBufferAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.living.LivingEntity;
import org.polyfrost.polyplus.client.nameplate.NameplateSelfVisibility;
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateDeferral;
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateFacing;
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    //? if >= 1.21.4 {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void polyplus$showOwnNameplate(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (NameplateSelfVisibility.appliesTo(entity)) {
            cir.setReturnValue(NameplateSelfVisibility.shouldShow(entity, distanceToCameraSq));
        }
    }
    //?} elif > 1.8.9 {
    /*@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void polyplus$showOwnNameplate(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (NameplateSelfVisibility.appliesTo(entity)) {
            cir.setReturnValue(NameplateBufferAdapter.showOwnName(entity));
        }
    }
    *///?} else {
    /*@Inject(method = "shouldRenderNameTag(Lnet/minecraft/entity/living/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void polyplus$showOwnNameplate(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (NameplateSelfVisibility.appliesTo(entity)) {
            cir.setReturnValue(NameplateSelfVisibility.shouldShow(entity));
        }
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;shouldRenderNameTag(Lnet/minecraft/entity/living/LivingEntity;)Z")
    )
    private boolean polyplus$deferNameplate(
        LivingEntityRenderer<?> renderer,
        LivingEntity entity,
        Operation<Boolean> original,
        @Local(argsOnly = true, ordinal = 0) double dx,
        @Local(argsOnly = true, ordinal = 1) double dy,
        @Local(argsOnly = true, ordinal = 2) double dz
    ) {
        return original.call(renderer, entity) && !LegacyNameplateDeferral.defer(renderer, entity, dx, dy, dz);
    }

    @ModifyExpressionValue(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;cameraPitch:F")
    )
    private float polyplus$faceCameraSneaking(float pitch) {
        return LegacyNameplateFacing.pitch(pitch);
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V")
    )
    private void polyplus$sneakBackground(
        Tesselator tesselator,
        Operation<Void> original,
        @Local(argsOnly = true) LivingEntity entity,
        @Local(ordinal = 0) String name
    ) {
        LegacyNameplateRenderer.sneakBackground(entity, name, tesselator, original);
    }

    @Inject(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V", ordinal = 1, shift = At.Shift.AFTER)
    )
    private void polyplus$sneakDecorations(LivingEntity entity, double x, double y, double z, CallbackInfo ci) {
        LegacyNameplateRenderer.beforeSneak(entity);
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I")
    )
    private int polyplus$sneakText(TextRenderer font, String text, int x, int y, int color, Operation<Integer> original, @Local(argsOnly = true) LivingEntity entity) {
        return LegacyNameplateRenderer.sneakText(font, entity, text, x, y, color, original);
    }
    *///?}
}
