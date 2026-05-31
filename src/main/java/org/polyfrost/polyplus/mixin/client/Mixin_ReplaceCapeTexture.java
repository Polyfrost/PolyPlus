package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
//? if >= 1.21.10 {
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

import org.polyfrost.polyplus.client.cosmetics.CosmeticManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class Mixin_ReplaceCapeTexture {
    @Shadow private PlayerInfo playerInfo;

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    void polyplus$onGetSkinTextures(CallbackInfoReturnable<PlayerSkin> cir) {
        if (this.playerInfo == null) {
            return;
        }

        //~ if >= 1.21.10 'getId' -> 'id'
        var capeLocation = CosmeticManager.get(this.playerInfo.getProfile().id(), "cape");
        if (capeLocation == null) {
            return;
        }

        var currentTextures = this.playerInfo.getSkin();
        var newSkinTextures = new PlayerSkin(
                //?if >= 1.21.10 {
                currentTextures.body(),
                new ClientAsset.ResourceTexture(capeLocation),
                currentTextures.elytra(),
                currentTextures.model(),
                currentTextures.secure()
                //?} else {
                /*currentTextures.texture(),
                currentTextures.textureUrl(),
                capeLocation,
                currentTextures.elytraTexture(),
                currentTextures.model(),
                currentTextures.secure()
                *///?}
        );

        cir.setReturnValue(newSkinTextures);
    }
}