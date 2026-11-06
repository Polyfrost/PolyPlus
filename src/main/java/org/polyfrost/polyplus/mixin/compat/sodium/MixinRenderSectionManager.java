package org.polyfrost.polyplus.mixin.compat.sodium;

import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.api.lobby.LobbyApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public class MixinRenderSectionManager {
    @Inject(method = "getSearchDistance", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$searchWholeLobby(CallbackInfoReturnable<Float> cir) {
        if (LobbyApi.isRendering()) cir.setReturnValue(Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f);
    }
}
