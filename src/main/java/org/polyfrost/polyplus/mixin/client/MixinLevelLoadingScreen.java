package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.polyplus.client.gui.MainMenuReplacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

//? if >= 1.21.9 {
import net.minecraft.client.gui.screens.LevelLoadingScreen;
//?} else {
/*import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Final;
*///?}

//? if >= 1.21.9 {
@Mixin(LevelLoadingScreen.class)
//?} else {
/*@Mixin(ReceivingLevelScreen.class)
*///?}
public class MixinLevelLoadingScreen {
    //? if >= 1.21.9 {
    @Shadow private LevelLoadingScreen.Reason reason;
    //?} else {
    /*@Shadow @Final private ReceivingLevelScreen.Reason reason;
    *///?}

    @Inject(
        //? if >= 26.1 {
        method = "extractBackground",
        //?} else {
        /*method = "renderBackground",
        *///?}
        at = @At("HEAD"),
        cancellable = true
    )
    private void polyplus$darkJoinBackground(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int mouseX, int mouseY, float tickDelta, CallbackInfo ci
    ) {
        //? if >= 1.21.9 {
        if (!MainMenuReplacement.enabled() || reason != LevelLoadingScreen.Reason.OTHER) return;
        //?} else {
        /*if (!MainMenuReplacement.enabled() || reason != ReceivingLevelScreen.Reason.OTHER) return;
        *///?}
        Screen self = (Screen) (Object) this;
        graphics.fill(0, 0, self.width, self.height, 0xFF0B1013);
        ci.cancel();
    }
}
//?}
