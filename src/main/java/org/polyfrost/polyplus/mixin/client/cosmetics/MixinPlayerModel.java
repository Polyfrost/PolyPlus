package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if >= 1.21.4
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess;
//? if < 1.21.4 {
/*import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess;
*///?}
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.polyfrost.polyplus.client.render.PlayerRenderContext;
import org.spongepowered.asm.mixin.Mixin;
//? if < 1.21.4
//import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
//? if < 1.21.4 {
/*import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
*///?}
//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} elif >= 1.21.4 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
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
