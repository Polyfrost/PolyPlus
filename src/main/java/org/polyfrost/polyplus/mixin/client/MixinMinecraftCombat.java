package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.server.bindings.CombatBindings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if > 1.8.9 {
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
//?} else {
/*import net.minecraft.world.HitResult;
*///?}

@Mixin(Minecraft.class)
public abstract class MixinMinecraftCombat {
    //? if > 1.8.9 {
    @WrapWithCondition(method = "startAttack", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;missTime:I", opcode = Opcodes.PUTFIELD))
    private boolean polyplus$missCooldown(Minecraft minecraft, int ticks) {
        return !polyplus$skipMissPenalty();
    }

    @WrapWithCondition(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private boolean polyplus$missStrengthReset(LocalPlayer player) {
        return !polyplus$skipMissPenalty();
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean polyplus$useWhileDigging(boolean destroying) {
        return destroying && !CombatBindings.allowDigAndUse;
    }

    @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean polyplus$digWhileUsing(boolean using) {
        return using && !CombatBindings.allowDigAndUse;
    }

    private boolean polyplus$skipMissPenalty() {
        HitResult hit = ((Minecraft) (Object) this).hitResult;
        return CombatBindings.skipMissPenalty(hit != null && hit.getType() == HitResult.Type.BLOCK);
    }
    //?} else {
    /*@WrapWithCondition(method = "doAttack", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;attackCooldown:I", opcode = Opcodes.PUTFIELD))
    private boolean polyplus$missCooldown(Minecraft minecraft, int ticks) {
        HitResult hit = ((Minecraft) (Object) this).crosshairTarget;
        return !CombatBindings.skipMissPenalty(hit != null && hit.type == HitResult.Type.BLOCK);
    }

    @ModifyExpressionValue(method = "doUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ClientPlayerInteractionManager;isMiningBlock()Z"))
    private boolean polyplus$useWhileDigging(boolean mining) {
        return mining && !CombatBindings.allowDigAndUse;
    }

    @ModifyExpressionValue(method = "handleMouseDown", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;hasItemInUse()Z"))
    private boolean polyplus$digWhileUsing(boolean using) {
        return using && !CombatBindings.allowDigAndUse;
    }
    *///?}
}
