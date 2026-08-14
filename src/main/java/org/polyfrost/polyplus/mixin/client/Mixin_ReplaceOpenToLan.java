package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.social.SocialOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.2 {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///?} else {
@Mixin(Minecraft.class)
//?}
public class Mixin_ReplaceOpenToLan {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void polyplus$replaceOpenToLan(Screen screen, CallbackInfo ci) {
        //? if >= 26.2 {
        /*return;
        *///?} else {
        if (!PolyPlusConfig.getReplacePauseLanButton()
            || !(screen instanceof net.minecraft.client.gui.screens.ShareToLanScreen)) {
            return;
        }
        Minecraft mc = (Minecraft) (Object) this;
        SocialOverlay.INSTANCE.openHostCurrentWorld(mc.screen);
        ci.cancel();
        //?}
    }
}
