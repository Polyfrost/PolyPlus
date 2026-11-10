package org.polyfrost.polyplus.mixin.client.pause;

//? if > 1.8.9 {
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if = 1.21.11 {
/*import net.minecraft.world.level.Level;
*///?} elif < 1.21.11 {
/*import net.minecraft.world.level.BlockGetter;
*///?}

@Mixin(Camera.class)
public abstract class MixinPauseCamera {
    @Shadow
    private Entity entity;

    @Shadow
    private Vec3 position;

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    //? if >= 26.1 {
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void polyplus$pauseStage(float partialTicks, CallbackInfo ci) {
        polyplus$applyStage(partialTicks);
    }
    //?} elif = 1.21.11 {
    /*@Inject(method = "setup", at = @At("TAIL"))
    private void polyplus$pauseStage(Level level, Entity entity, boolean detached, boolean mirrored, float partialTicks, CallbackInfo ci) {
        polyplus$applyStage(partialTicks);
    }
    *///?} else {
    /*@Inject(method = "setup", at = @At("TAIL"))
    private void polyplus$pauseStage(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTicks, CallbackInfo ci) {
        polyplus$applyStage(partialTicks);
    }
    *///?}

    @Unique
    private void polyplus$applyStage(float partialTicks) {
        if (!(this.entity instanceof LivingEntity living)) return;
        PauseStage.Pose pose = PauseStage.pose(living, partialTicks, this.position.x, this.position.y, this.position.z, this.yRot, this.xRot, Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight));
        if (pose == null) return;
        this.setRotation(pose.yaw, pose.pitch);
        this.setPosition(new Vec3(pose.x, pose.y, pose.z));
    }
}
//?}
