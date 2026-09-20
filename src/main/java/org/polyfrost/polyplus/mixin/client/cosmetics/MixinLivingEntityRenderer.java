package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if = 1.8.9 {
/*import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.entity.living.LivingEntity;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @Shadow
    protected Model model;

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
