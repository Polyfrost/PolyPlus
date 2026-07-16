//? if >= 1.21.4 {
package org.polyfrost.polyplus.mixin.client.cosmetics;

import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;
//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
//?} else {
/*import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}

//? if >= 1.21.10 {
@Mixin(AvatarRenderer.class)
//?} else {
/*@Mixin(PlayerRenderer.class)
*///?}
public class AvatarRendererMixin {
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
}
//?}
