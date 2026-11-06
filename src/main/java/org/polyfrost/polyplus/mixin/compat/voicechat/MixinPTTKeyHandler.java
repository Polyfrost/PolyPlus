package org.polyfrost.polyplus.mixin.compat.voicechat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.polyfrost.polyplus.client.party.PartySvc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.PTTKeyHandler", remap = false)
public class MixinPTTKeyHandler {
    @ModifyReturnValue(method = "isPTTDown", at = @At("RETURN"), remap = false, require = 0, expect = 0)
    private boolean polyplus$sharePushToTalk(boolean down) {
        return PartySvc.INSTANCE.sharePushToTalk(down);
    }
}
