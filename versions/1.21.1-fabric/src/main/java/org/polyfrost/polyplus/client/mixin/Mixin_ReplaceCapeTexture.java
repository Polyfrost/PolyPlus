package org.polyfrost.polyplus.client.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
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

        var capeLocation = CosmeticManager.get(this.playerInfo.getProfile().getId(), "cape");
        if (capeLocation == null) {
            return;
        }

        var currentTextures = this.playerInfo.getSkin();
        var newSkinTextures = new PlayerSkin(
                currentTextures.texture(),
                currentTextures.textureUrl(),
                capeLocation,
                currentTextures.elytraTexture(),
                currentTextures.model(),
                currentTextures.secure()
        );

        cir.setReturnValue(newSkinTextures);
    }
}
