package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if > 1.8.9 {
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.polyfrost.polyplus.client.render.PlayerRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?}

//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?}

//? if >= 1.21.4 {
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess;
//?}

//? if < 1.21.11 {
/*import net.minecraft.client.model.PlayerModel;
*///?}

//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}

//? if = 1.21.1 {
/*import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess;
import org.spongepowered.asm.mixin.Unique;
*///?}

@Mixin(PlayerModel.class)
public class MixinPlayerModel
    //? if < 1.21.4
    //implements PlayerModelRootAccess
{

    //? if < 1.21.4 {
    /*@Unique
    private ModelPart polyplus$root;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyplus$captureRoot(ModelPart root, boolean thinArms, CallbackInfo ci) {
        polyplus$root = root;
    }

    @Override
    public ModelPart polyplus$root() {
        return polyplus$root;
    }
    *///?}

    //? if >= 1.21.10 {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void polyplus$applyEmote(AvatarRenderState state, CallbackInfo ci) {
    //?} elif >= 1.21.4 {
    /*@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("RETURN"))
    private void polyplus$applyEmote(PlayerRenderState state, CallbackInfo ci) {
    *///?} else {
    /*@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void polyplus$applyEmote(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float yRot, float xRot, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player) || !(player instanceof PlayerEmotesAccess playerAccess))
            return;

        EmoteController controller = playerAccess.polyplus$emoteController();
        if (!controller.isActive())
            return;

        PlayerModel model = (PlayerModel) (Object) this;
        controller.applyToModel(model, PlayerRenderContext.Companion.from(player, 0f, limbSwingAmount, ageInTicks));
        return;
    *///?}
        //? if >= 1.21.4 {
        if (!(state instanceof AvatarEmoteRenderAccess renderAccess))
            return;

        EmoteController controller = renderAccess.polyplus$boundEmoteController();
        if (!controller.isActive())
            return;

        PlayerModel model = (PlayerModel) (Object) this;
        renderAccess.polyplus$setLastEmoteSample(controller.applyToModel(model, PlayerRenderContext.Companion.from(state)));
        //?}
    }
}
//?} else {
/*import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.entity.Entity;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess;
import org.polyfrost.polyplus.client.emotes.playback.ModelPoseApplicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class MixinPlayerModel implements PlayerModelRootAccess {
    @Shadow public ModelPart head;
    @Shadow public ModelPart hat;
    @Shadow public ModelPart body;
    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;

    @Unique
    private ModelPart polyplus$root;

    @Unique
    private float[] polyplus$pivots;

    @Unique
    private boolean polyplus$posed;

    @Unique
    private ModelPart[] polyplus$parts() {
        return new ModelPart[]{head, hat, body, rightArm, leftArm, rightLeg, leftLeg};
    }

    @Override
    public ModelPart polyplus$root() {
        if (polyplus$root == null) {
            HumanoidModel model = (HumanoidModel) (Object) this;
            polyplus$root = new ModelPart(model);
            model.parts.remove(polyplus$root);
        }
        return polyplus$root;
    }

    @Override
    public void polyplus$resetPart(ModelPart part) {
        ModelPart[] parts = polyplus$parts();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == part) {
                part.x = polyplus$pivots[i * 3];
                part.y = polyplus$pivots[i * 3 + 1];
                part.z = polyplus$pivots[i * 3 + 2];
                part.rotationX = part.rotationY = part.rotationZ = 0f;
                return;
            }
        }
        if (part == polyplus$root) {
            part.x = part.y = part.z = 0f;
            part.rotationX = part.rotationY = part.rotationZ = 0f;
        }
    }

    @Override
    public void polyplus$markPosed() {
        polyplus$posed = true;
    }

    @Inject(method = "setupAnimation", at = @At("HEAD"))
    private void polyplus$resetEmotePose(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity, CallbackInfo ci) {
        ModelPart[] parts = polyplus$parts();
        if (polyplus$pivots == null) {
            polyplus$pivots = new float[parts.length * 3];
            for (int i = 0; i < parts.length; i++) {
                polyplus$pivots[i * 3] = parts[i].x;
                polyplus$pivots[i * 3 + 1] = parts[i].y;
                polyplus$pivots[i * 3 + 2] = parts[i].z;
            }
        }
        if (!polyplus$posed) return;
        polyplus$posed = false;
        for (ModelPart part : parts) polyplus$resetPart(part);
        polyplus$resetPart(polyplus$root());
    }

    @Inject(method = "setupAnimation", at = @At("RETURN"))
    private void polyplus$applyEmote(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity, CallbackInfo ci) {
        ModelPoseApplicator.afterSetupAnimation((HumanoidModel) (Object) this, entity, walkAnimationSpeed, bob);
    }
}
*///?}
