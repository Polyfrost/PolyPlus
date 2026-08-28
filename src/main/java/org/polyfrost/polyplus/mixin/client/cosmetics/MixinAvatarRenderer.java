package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if >= 1.21.4 {
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
//?}
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticRenderLayer;
import org.polyfrost.polyplus.client.emotes.effects.EmoteEffectRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
//?} else {
/*import net.minecraft.client.renderer.entity.player.PlayerRenderer;
*///?}
//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}

//? if >= 1.21.10 {
@Mixin(AvatarRenderer.class)
//?} else {
/*@Mixin(PlayerRenderer.class)
*///?}
public class MixinAvatarRenderer {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyplus$addEffectLayer(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
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
    }

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
}
