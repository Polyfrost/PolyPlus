package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if > 1.8.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.polyfrost.polyplus.client.lobby.LobbyAntics;
import org.polyfrost.polyplus.client.lobby.LobbyHerobrine;
import org.polyfrost.polyplus.client.lobby.LobbyHerobrineLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticRenderLayer;
import org.polyfrost.polyplus.client.emotes.effects.EmoteEffectRenderLayer;
import org.polyfrost.polyplus.compat.EntityModelFeaturesCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?}

//? if >= 1.21.10 {
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.polyfrost.polyplus.client.cosmetics.render.PreviewCapeLayer;
//?}

//? if >= 1.21.4 {
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseAvatar;
import org.polyfrost.polyplus.client.lobby.LobbyAvatar;
import org.polyfrost.polyplus.compat.NotEnoughAnimationsCompat;
//?}

//? if >= 1.21.10 {
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer;
//?}

//? if < 1.21.11 {
/*import net.minecraft.client.model.PlayerModel;
*///?}

//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}

//? if < 1.21.10 {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
*///?}

//? if >= 1.21.10 {
@Mixin(AvatarRenderer.class)
//?} else {
/*@Mixin(PlayerRenderer.class)
*///?}
public class MixinAvatarRenderer {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyplus$addEffectLayer(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
        EntityModelFeaturesCompat.register();
        LivingEntityRendererInvoker invoker = (LivingEntityRendererInvoker) this;

        //? if >= 1.21.10 {
        @SuppressWarnings("unchecked")
        RenderLayerParent<AvatarRenderState, PlayerModel> parent =
            (RenderLayerParent<AvatarRenderState, PlayerModel>) this;
        //?} elif >= 1.21.4 {
        /*@SuppressWarnings("unchecked")
        RenderLayerParent<PlayerRenderState, PlayerModel> parent =
            (RenderLayerParent<PlayerRenderState, PlayerModel>) this;
        *///?} else {
        /*@SuppressWarnings("unchecked")
        RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent =
            (RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) this;
        *///?}

        invoker.polyplus$invokeAddLayer(new EmoteEffectRenderLayer(parent));
        invoker.polyplus$invokeAddLayer(new CosmeticRenderLayer(parent));
        invoker.polyplus$invokeAddLayer(new LobbyHerobrineLayer(parent));

        //? if >= 1.21.10 {
        if (FabricLoader.getInstance().isModLoaded("waveycapes")) {
            invoker.polyplus$layers().add(
                new PreviewCapeLayer(parent, context.getModelSet(), context.getEquipmentAssets()));
        }
        //?}
    }
//?}

    //? if >= 1.21.4 {
    //? if >= 1.21.10 {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("RETURN")
    )
    private void polyplus$bindEmoteState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
    //?} else {
    /*@Inject(
        method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
        at = @At("RETURN")
    )
    private void polyplus$bindEmoteState(AbstractClientPlayer entity, PlayerRenderState state, float partialTicks, CallbackInfo ci) {
    *///?}
        PauseAvatar.apply(entity, state);
        //? if >= 1.21.10 {
        if (entity instanceof LobbyAvatar || PlayerPreviewRenderer.isPreviewEntity(entity)) {
        //?} else {
        /*if (entity instanceof LobbyAvatar) {
        *///?}
            NotEnoughAnimationsCompat.detach(state);
        }
        if (!(state instanceof AvatarEmoteRenderAccess renderAccess)) {
            return;
        }

        EmoteController controller = entity instanceof PlayerEmotesAccess playerAccess
            ? playerAccess.polyplus$emoteController()
            : null;

        if (controller != null) {
            renderAccess.polyplus$bindEmoteController(controller);
        }
    }
    //?}

    //? if >= 1.21.10 {
    @Inject(
        method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
        at = @At("HEAD")
    )
    private void polyplus$leanHerobrine(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        LobbyHerobrine.lean(state.id, poseStack);
        LobbyAntics.spin(state.id, poseStack);
    }
    //?} elif >= 1.21.4 {
    /*@Inject(
        method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
        at = @At("HEAD")
    )
    private void polyplus$leanHerobrine(PlayerRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        LobbyHerobrine.lean(state.id, poseStack);
        LobbyAntics.spin(state.id, poseStack);
    }
    *///?} elif > 1.8.9 {
    /*@Inject(
        method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
        at = @At("HEAD")
    )
    private void polyplus$leanHerobrine(AbstractClientPlayer player, PoseStack poseStack, float bob, float bodyRot, float partialTick, float scale, CallbackInfo ci) {
        LobbyHerobrine.lean(player.getId(), poseStack);
        LobbyAntics.spin(player.getId(), poseStack);
    }
    *///?}
//? if > 1.8.9
}

//? if = 1.8.9 {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.polyfrost.polyplus.client.lobby.LobbyAntics;
import org.polyfrost.polyplus.client.lobby.LobbyHerobrine;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticRenderLayer;
import org.polyfrost.polyplus.client.emotes.effects.EmoteEffectRenderLayer;
import org.polyfrost.polyplus.client.emotes.playback.ModelPoseApplicator;
import org.polyfrost.polyplus.client.lobby.LobbyHerobrineLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class MixinAvatarRenderer {
    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("RETURN"))
    private void polyplus$addCosmeticLayer(EntityRenderDispatcher dispatcher, boolean thinArms, CallbackInfo ci) {
        LivingEntityRendererInvoker invoker = (LivingEntityRendererInvoker) this;
        invoker.polyplus$invokeAddLayer(new EmoteEffectRenderLayer((PlayerRenderer) (Object) this));
        invoker.polyplus$invokeAddLayer(new CosmeticRenderLayer((PlayerRenderer) (Object) this));
        invoker.polyplus$invokeAddLayer(new LobbyHerobrineLayer((PlayerRenderer) (Object) this));
    }

    @Inject(method = {"renderRightArm", "renderLeftArm"}, at = @At("HEAD"))
    private void polyplus$enterFirstPersonHand(CallbackInfo ci) {
        ModelPoseApplicator.firstPersonHand = true;
    }

    @Inject(method = {"renderRightArm", "renderLeftArm"}, at = @At("RETURN"))
    private void polyplus$exitFirstPersonHand(CallbackInfo ci) {
        ModelPoseApplicator.firstPersonHand = false;
    }

    @Inject(method = "applyRotation(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;FFF)V", at = @At("HEAD"))
    private void polyplus$leanHerobrine(ClientPlayerEntity player, float bob, float bodyYaw, float tickDelta, CallbackInfo ci) {
        LobbyHerobrine.lean(player.getNetworkId());
        LobbyAntics.spin(player.getNetworkId());
    }
}
*///?}
