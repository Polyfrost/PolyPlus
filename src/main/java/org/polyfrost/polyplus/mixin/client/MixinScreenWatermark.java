package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.polyplus.client.gui.ClientWatermark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

@Mixin(Screen.class)
public class MixinScreenWatermark {
    @Inject(
        //? if >= 26.1 {
        method = "extractRenderStateWithTooltipAndSubtitles",
        //?} else if >= 1.21.10 {
        /*method = "renderWithTooltipAndSubtitles",
        *///?} else {
        /*method = "renderWithTooltip",
        *///?}
        at = @At("TAIL")
    )
    private void polyplus$drawWatermark(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int mouseX, int mouseY, float tickDelta, CallbackInfo ci
    ) {
        ClientWatermark.render(graphics, (Screen) (Object) this);
    }
}
//?} else {
/*import net.minecraft.client.render.GameRenderer;
import org.polyfrost.polyplus.client.gui.ClientWatermark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinScreenWatermark {
    @Inject(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V", shift = At.Shift.AFTER))
    private void polyplus$drawWatermark(float tickDelta, long startTime, CallbackInfo ci) {
        ClientWatermark.render();
    }
}
*///?}
