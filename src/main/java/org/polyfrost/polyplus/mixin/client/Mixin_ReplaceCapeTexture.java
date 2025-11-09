package org.polyfrost.polyplus.mixin.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import org.polyfrost.polyplus.client.cosmetics.CosmeticManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetworkPlayerInfo.class)
public class Mixin_ReplaceCapeTexture {
    @Shadow @Final private GameProfile gameProfile;

    @Inject(method = "getLocationCape", at = @At("HEAD"), cancellable = true)
    private void polyplus$useCustomCape(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation capeLocation = CosmeticManager.get(this.gameProfile.getId(), "cape");
        if (capeLocation != null) {
            cir.setReturnValue(capeLocation);
        }
    }
}
