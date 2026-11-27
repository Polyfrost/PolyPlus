package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if >= 1.21.4 && < 1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityPoseInvoker {
    @Invoker("setupRotations")
    void polyplus$setupRotations(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale);

    @Invoker("scale")
    void polyplus$scale(LivingEntityRenderState state, PoseStack poseStack);
}
*///?} elif = 1.21.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityPoseInvoker {
    @Invoker("setupRotations")
    void polyplus$setupRotations(LivingEntity entity, PoseStack poseStack, float bob, float bodyRot, float partialTick, float scale);

    @Invoker("scale")
    void polyplus$scale(LivingEntity entity, PoseStack poseStack, float partialTick);

    @Invoker("getBob")
    float polyplus$getBob(LivingEntity entity, float partialTick);

    @Invoker("getAttackAnim")
    float polyplus$getAttackAnim(LivingEntity entity, float partialTick);
}
*///?} elif = 1.8.9 {
/*import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityPoseInvoker {
    @Invoker("getAttackAnimationProgress")
    float polyplus$getAttackAnimationProgress(LivingEntity entity, float tickDelta);

    @Invoker("getRotatedAngle")
    float polyplus$getRotatedAngle(float previous, float current, float tickDelta);

    @Invoker("getBob")
    float polyplus$getBob(LivingEntity entity, float tickDelta);
}
*///?}
