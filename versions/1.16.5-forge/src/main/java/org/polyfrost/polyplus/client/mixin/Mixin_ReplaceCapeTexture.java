package org.polyfrost.polyplus.client.mixin;


import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.polyfrost.polyplus.PolyPlus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class Mixin_ReplaceCapeTexture {
    @Shadow
    private PlayerInfo playerInfo;

    @Inject(method = "getCloakTextureLocation", at = @At("RETURN"), cancellable = true)
    private void polyplus$onGetCapeTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        cir.setReturnValue(new ResourceLocation(PolyPlus.ID, "randomcape2.png"));
    }
}
