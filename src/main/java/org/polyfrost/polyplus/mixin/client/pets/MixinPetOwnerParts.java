package org.polyfrost.polyplus.mixin.client.pets;

import org.polyfrost.polyplus.client.pets.PetOwnerParts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} elif > 1.8.9 {
/*import net.minecraft.client.model.PlayerModel;
*///?}

//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} elif >= 1.21.4 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?} elif > 1.8.9 {
/*import net.minecraft.world.entity.LivingEntity;
*///?} else {
/*import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.polyfrost.polyplus.client.emotes.playback.EmotePoser;
*///?}

//? if > 1.8.9 {
@Mixin(value = PlayerModel.class, priority = 1500)
//?} else {
/*@Mixin(value = HumanoidModel.class, priority = 1500)
*///?}
public class MixinPetOwnerParts {
    //? if >= 1.21.10 {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void polyplus$capturePetOwner(AvatarRenderState state, CallbackInfo ci) {
        PetOwnerParts.capture(state.id, (PlayerModel) (Object) this);
    }
    //?} elif >= 1.21.4 {
    /*@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("RETURN"))
    private void polyplus$capturePetOwner(PlayerRenderState state, CallbackInfo ci) {
        PetOwnerParts.capture(state.id, (PlayerModel) (Object) this);
    }
    *///?} elif > 1.8.9 {
    /*@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void polyplus$capturePetOwner(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float yRot, float xRot, CallbackInfo ci) {
        if (entity == null) return;
        PetOwnerParts.capture(entity.getId(), (PlayerModel) (Object) this);
    }
    *///?} else {
    /*@Inject(method = "setupAnimation", at = @At("RETURN"))
    private void polyplus$capturePetOwner(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity, CallbackInfo ci) {
        if (EmotePoser.firstPersonHand || !(entity instanceof PlayerEntity) || !((Object) this instanceof PlayerModel)) return;
        PetOwnerParts.capture(entity.getNetworkId(), (PlayerModel) (Object) this);
    }
    *///?}
}
