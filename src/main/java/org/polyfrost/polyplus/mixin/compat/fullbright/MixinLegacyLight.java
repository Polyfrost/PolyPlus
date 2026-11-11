package org.polyfrost.polyplus.mixin.compat.fullbright;

import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.api.lobby.LobbyApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.polyfrost.fullbright.legacy.LegacyLight", remap = false)
public class MixinLegacyLight {
    @Inject(method = {"shouldBrighten", "shouldBrightenOnClient"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$keepLobbyLighting(CallbackInfoReturnable<Boolean> cir) {
        if (polyplus$outsideWorld()) cir.setReturnValue(false);
    }

    @Unique
    private static boolean polyplus$outsideWorld() {
        //? if > 1.8.9 {
        return LobbyApi.isRendering() || LobbyApi.isBuilding() || Minecraft.getInstance().level == null;
        //?} else {
        /*return LobbyApi.isRendering() || LobbyApi.isBuilding() || Minecraft.getInstance().world == null;
        *///?}
    }
}
