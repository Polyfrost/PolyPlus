package org.polyfrost.polyplus.mixin.compat.evergreenhud;

import org.polyfrost.polyplus.client.gui.MainMenuReplacement;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.media.MediaHud", remap = false)
public class MixinEvergreenMediaHud {
    @Inject(method = "click", at = @At("HEAD"), cancellable = true, require = 0)
    private void polyplus$ignoreClicksBehindMenu(CallbackInfo ci) {
        if (LobbyScene.showing() || MainMenuReplacement.isMenu(ClientPlatform.INSTANCE.currentScreen())) ci.cancel();
    }
}
