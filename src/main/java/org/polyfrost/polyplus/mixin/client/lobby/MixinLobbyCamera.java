package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.1 {
import net.minecraft.client.DeltaTracker;
//?} elif = 1.21.11 {
/*import net.minecraft.world.level.Level;
*///?} else {
/*import net.minecraft.world.level.BlockGetter;
*///?}

@Mixin(value = Camera.class, priority = 2000)
public abstract class MixinLobbyCamera {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    //? if >= 26.1 {
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateFov(F)F"))
    private void polyplus$holdLobbyView(DeltaTracker deltaTracker, CallbackInfo ci) {
        polyplus$hold(((Camera) (Object) this).getCameraEntityPartialTicks(deltaTracker));
    }
    //?} elif = 1.21.11 {
    /*@Inject(method = "setup", at = @At("TAIL"))
    private void polyplus$holdLobbyView(Level level, Entity entity, boolean detached, boolean mirrored, float partialTicks, CallbackInfo ci) {
        polyplus$hold(partialTicks);
    }
    *///?} else {
    /*@Inject(method = "setup", at = @At("TAIL"))
    private void polyplus$holdLobbyView(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTicks, CallbackInfo ci) {
        polyplus$hold(partialTicks);
    }
    *///?}

    @Unique
    private void polyplus$hold(float partialTicks) {
        Entity rig = LobbyScene.heldRig();
        if (rig == null) return;
        this.detached = false;
        this.setRotation(rig.getViewYRot(partialTicks), rig.getViewXRot(partialTicks));
        this.setPosition(rig.getEyePosition(partialTicks));
    }
}
//?}
