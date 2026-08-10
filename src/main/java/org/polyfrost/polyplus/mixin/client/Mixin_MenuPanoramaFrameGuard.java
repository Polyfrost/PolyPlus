//? if >= 26.1 {
package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class Mixin_MenuPanoramaFrameGuard {
    @Inject(method = "render", at = @At("HEAD"))
    private void polyplus$dropCoveredPanorama(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (!MenuPanorama.suppressPanorama()) return;

        GameRenderer self = (GameRenderer) (Object) this;
        //? if >= 26.2 {
        GuiRenderState state = self.gameRenderState().guiRenderState;
        //?} else {
        /*GuiRenderState state = self.getGameRenderState().guiRenderState;
        *///?}
        if (state.panoramaRenderState == null) return;
        state.panoramaRenderState = null;

        if (MenuPanorama.backdropFilled()) return;
        //? if >= 26.2 {
        net.minecraft.util.ARGB.setVector4fFromARGB32(state.clearColorOverride, MenuPanorama.BASE_COLOR);
        //?} else {
        /*state.clearColorOverride = MenuPanorama.BASE_COLOR;
        *///?}
    }
}
//?}
