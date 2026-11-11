package org.polyfrost.polyplus.mixin.client.ping;

import org.polyfrost.polyplus.client.gui.WheelOverlay;
import org.polyfrost.polyplus.client.ping.PingMarkers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if > 1.8.9 {
import net.minecraft.client.DeltaTracker;
//?}

//? if >= 26.2 {
import net.minecraft.client.gui.Hud;
//?} elif > 1.8.9 {
/*import net.minecraft.client.gui.Gui;
*///?} else {
/*import net.minecraft.client.gui.GameGui;
*///?}

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} elif > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

//? if >= 26.2 {
@Mixin(Hud.class)
//?} elif > 1.8.9 {
/*@Mixin(Gui.class)
*///?} else {
/*@Mixin(GameGui.class)
*///?}
public class MixinPingHud {
    //? if >= 26.1 {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void polyplus$drawPings(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        PingMarkers.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
        WheelOverlay.render(graphics);
    }
    //?} elif > 1.8.9 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void polyplus$drawPings(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        PingMarkers.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
        WheelOverlay.render(graphics);
    }
    *///?} else {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void polyplus$drawPings(float tickDelta, CallbackInfo ci) {
        PingMarkers.render(null, tickDelta);
        WheelOverlay.render(null);
    }
    *///?}
}
