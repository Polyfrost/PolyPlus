package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreen {
    //? if >= 26.1 {
    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void polyplus$keepLobbyBackdrop(GuiGraphicsExtractor graphics, float partialTick, CallbackInfo ci) {
        if (LobbyScene.showing()) ci.cancel();
    }
    //?} else {
    /*@Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void polyplus$keepLobbyBackdrop(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (LobbyScene.showing()) ci.cancel();
    }
    *///?}
}
//?}
