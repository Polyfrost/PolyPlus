package org.polyfrost.polyplus.mixin.compat.essential;

//? if >= 26.1 {
import org.polyfrost.polyplus.client.gui.vanillamenu.EssentialMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Essential puts its own sidebar, player and "NEW" badge on the title screen whenever this says it's enabled
 * (the draw path and both of its proxy button mixins ask it). While our vanilla menu is on, only ours shows;
 * Essential's screens stay reachable through our Essential buttons.
 */
@Pseudo
@Mixin(targets = "gg.essential.handlers.PauseMenuDisplay$Companion", remap = false)
public class MixinEssentialTitleMenu {
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void polyplus$oneMenuOnly(CallbackInfoReturnable<Boolean> cir) {
        if (EssentialMenu.hidesTitleLayer()) cir.setReturnValue(false);
    }
}
//?}
