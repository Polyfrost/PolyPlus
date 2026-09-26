package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if wwaypoints {
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.compat.WWaypointsCompat;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Pseudo
@Mixin(targets = "com.wwaypoints.client.screen.ToggleSneakConfigScreen", remap = false)
public class MixinToggleSneakConfigScreen {
    //? if wwaypoints {
    @Unique
    private int polyplus$rawMouseX;
    @Unique
    private int polyplus$rawMouseY;

    @Inject(method = "render", at = @At("HEAD"))
    private void captureMouse(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        polyplus$rawMouseX = mouseX;
        polyplus$rawMouseY = mouseY;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderDisabledReason(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        for (GuiEventListener child : ((Screen) (Object) this).children()) {
            if (child instanceof AbstractWidget widget && !widget.active && widget.isHovered()) {
                guiGraphics.setTooltipForNextFrame(Component.literal(WWaypointsCompat.DISABLED_REASON), polyplus$rawMouseX, polyplus$rawMouseY);
                return;
            }
        }
    }
    //?}
}
