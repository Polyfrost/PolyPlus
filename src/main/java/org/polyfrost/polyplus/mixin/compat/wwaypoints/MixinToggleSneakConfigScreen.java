package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if wwaypoints {
import com.wwaypoints.client.screen.UiToggle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.compat.WWaypointsCompat;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Pseudo
@Mixin(targets = "com.wwaypoints.client.screen.ToggleSneakConfigScreen", remap = false)
public class MixinToggleSneakConfigScreen {
    //? if wwaypoints {
    @Shadow
    private UiToggle staySneakedInContainersToggle;

    @Unique
    private int polyplus$rawMouseX;
    @Unique
    private int polyplus$rawMouseY;

    @Inject(method = "init", at = @At("TAIL"))
    private void polyplus$lockStaySneaked(CallbackInfo ci) {
        if (staySneakedInContainersToggle != null) staySneakedInContainersToggle.active = false;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void polyplus$captureMouse(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        polyplus$rawMouseX = mouseX;
        polyplus$rawMouseY = mouseY;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void polyplus$renderDisabledReason(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (staySneakedInContainersToggle != null && staySneakedInContainersToggle.isHovered()) {
            guiGraphics.setTooltipForNextFrame(Component.literal(WWaypointsCompat.DISABLED_REASON), polyplus$rawMouseX, polyplus$rawMouseY);
        }
    }
    //?}
}
