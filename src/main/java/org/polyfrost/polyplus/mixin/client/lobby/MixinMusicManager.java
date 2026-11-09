package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
import net.minecraft.client.sounds.MusicManager;
//?} else {
/*import net.minecraft.client.sound.MusicManager;
*///?}
import org.polyfrost.polyplus.client.lobby.LobbyAmbience;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public class MixinMusicManager {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void polyplus$holdForLobbyAmbience(CallbackInfo ci) {
        if (LobbyAmbience.holdsMusic()) ci.cancel();
    }
}
