package org.polyfrost.polyplus.mixin.client.nameplate.render;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.polyfrost.polyplus.client.nameplate.render.LegacyNameTagDeferral;
import org.polyfrost.polyplus.client.nameplate.render.NameTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinNameTagSneak {
    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;shouldRenderNameTag(Lnet/minecraft/entity/living/LivingEntity;)Z")
    )
    private boolean polyplus$deferNameTag(
        LivingEntityRenderer<?> renderer,
        LivingEntity entity,
        Operation<Boolean> original,
        @Local(argsOnly = true, ordinal = 0) double x,
        @Local(argsOnly = true, ordinal = 1) double y,
        @Local(argsOnly = true, ordinal = 2) double z
    ) {
        return original.call(renderer, entity) && !LegacyNameTagDeferral.defer(renderer, entity, x, y, z);
    }

    @Inject(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;getInstance()Lnet/minecraft/client/render/vertex/Tesselator;")
    )
    private void polyplus$beginSneakingNameTag(LivingEntity entity, double x, double y, double z, CallbackInfo ci) {
        NameTags.beginSneaking(entity);
    }

    @ModifyArg(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;rotatef(FFFF)V", ordinal = 1),
        index = 0
    )
    private float polyplus$faceCamera(float pitch) {
        return NameTags.cameraPitch(pitch);
    }

    @ModifyArg(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;"),
        index = 3
    )
    private float polyplus$hideSneakingBackground(float alpha) {
        return NameTags.backgroundAlpha(alpha);
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I")
    )
    private int polyplus$drawSneakingNameText(TextRenderer font, String text, int x, int y, int color, Operation<Integer> original) {
        return NameTags.drawName(font, text, x, y, color, original);
    }
}
*///?}
