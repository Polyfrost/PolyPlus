package org.polyfrost.polyplus.client.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import org.polyfrost.polyplus.PolyPlus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.polyfrost.polyplus.network.plus.cache.CosmeticCache.getCosmetic;

@Mixin(NetworkPlayerInfo.class)
public class Mixin_ReplaceCapeTexture {
    @Shadow
    private ResourceLocation locationCape;

    @Shadow @Final
    private GameProfile gameProfile;

    @Inject(method = "getLocationCape", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void polyplus$onGetCape(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation cape = getCosmetic(gameProfile.getId(), "cape");
        if (cape != null) {
            System.out.println("Replacing cape for " + gameProfile.getName() + " with " + cape);
            this.locationCape = cape;
        } else {
            System.out.println("No cape found for " + gameProfile.getName());
        }
    }
}
