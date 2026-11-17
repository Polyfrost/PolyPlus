package org.polyfrost.polyplus.mixin.compat.oneconfig;

import org.polyfrost.oneconfig.internal.ui.sound.UiSoundService;
import org.polyfrost.oneconfig.internal.ui.sound.UiSounds;
import org.polyfrost.polyplus.client.lobby.AmbienceQuiet;
import org.polyfrost.polyplus.client.lobby.LobbyAmbience;
import org.polyfrost.polyplus.client.lobby.UiAmbienceAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.sound.UiSounds", remap = false)
public abstract class MixinUiSounds implements UiAmbienceAccess {
    @Shadow(remap = false)
    private static boolean ambienceActive;

    @Shadow(remap = false)
    private UiSoundService getService() {
        return null;
    }

    @Shadow(remap = false)
    private void startAmbienceIfEnabled() {
    }

    @Inject(method = "startAmbienceIfEnabled", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$yieldToLobby(CallbackInfo ci) {
        if (LobbyAmbience.holdsMusic() || AmbienceQuiet.getQuiet()) ci.cancel();
    }

    @Override
    public void polyplus$setQuiet(boolean quiet) {
        synchronized (UiSounds.class) {
            if (!ambienceActive) return;
            if (!quiet) {
                startAmbienceIfEnabled();
                return;
            }
            UiSoundService service = getService();
            if (service != null) service.stopAmbience(false);
        }
    }
}
