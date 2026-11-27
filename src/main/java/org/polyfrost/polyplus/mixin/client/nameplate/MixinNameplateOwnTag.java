package org.polyfrost.polyplus.mixin.client.nameplate;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.polyfrost.polyplus.client.nameplate.NameplateOwnTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if > 1.8.9 {
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
//?} else {
/*import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
*///?}
//? if = 1.21.1
/*import net.minecraft.client.Minecraft;*/

@Mixin(LivingEntityRenderer.class)
public class MixinNameplateOwnTag {
    //? if >= 1.21.4 {
    @ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"))
    private boolean polyplus$showOwnNameplate(boolean original, LivingEntity entity, double distanceToCameraSq) {
        return NameplateOwnTag.shows(original, entity, distanceToCameraSq);
    }
    //?} elif = 1.21.1 {
    /*@ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"))
    private boolean polyplus$showOwnNameplate(boolean original, LivingEntity entity) {
        if (original) return true;
        return NameplateOwnTag.shows(false, entity, Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity));
    }
    *///?} else {
    /*@ModifyReturnValue(method = "shouldRenderNameTag(Lnet/minecraft/entity/living/LivingEntity;)Z", at = @At("RETURN"))
    private boolean polyplus$showOwnNameplate(boolean original, LivingEntity entity) {
        if (original) return true;
        return NameplateOwnTag.shows(false, entity, NameplateOwnTag.cameraDistanceSq(entity));
    }
    *///?}
}
