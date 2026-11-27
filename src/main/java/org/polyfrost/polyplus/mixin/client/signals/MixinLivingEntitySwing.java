package org.polyfrost.polyplus.mixin.client.signals;

//? if >= 26.3 {
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySwing {
    @Inject(
        method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",
        at = @At("RETURN")
    )
    private void polyplus$signalSwing(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            PlayerSignals.onSwingStart((LivingEntity) (Object) this);
        }
    }
}
//?} elif > 1.8.9 {
/*import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySwing {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("RETURN"))
    private void polyplus$signalSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.swinging && self.swingTime == -1) {
            PlayerSignals.onSwingStart(self);
        }
    }
}
*///?} else {
/*import net.minecraft.entity.living.LivingEntity;
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySwing {
    @Inject(method = "swingArm()V", at = @At("RETURN"))
    private void polyplus$signalSwing(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.armSwinging && self.armSwingingTicks == -1) {
            PlayerSignals.onSwingStart(self);
        }
    }
}
*///?}
