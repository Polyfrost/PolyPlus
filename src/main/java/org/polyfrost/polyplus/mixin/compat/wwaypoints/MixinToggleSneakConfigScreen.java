package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if wwaypoints {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
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
    //? if >= 1.21.6 {
    @Unique
    private int polyplus$rawMouseX;
    @Unique
    private int polyplus$rawMouseY;

    @Inject(method = "render", at = @At("HEAD"))
    private void captureMouse(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        polyplus$rawMouseX = mouseX;
        polyplus$rawMouseY = mouseY;
    }
    //?}

    // 0.8.1 only greys out the toggle and indicator controls when the server disables the whole mod
    @IfModLoaded(value = "wwaypoints", minVersion = "0.8.1")
    @ModifyExpressionValue(method = "updatePolicyControls", at = @At(value = "INVOKE", target = "Lcom/wwaypoints/client/ServerFeaturePolicy;allowsMod()Z"), remap = false)
    private boolean lockToggleControls(boolean original) {
        return false;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderDisabledReason(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        for (GuiEventListener child : ((Screen) (Object) this).children()) {
            if (child instanceof AbstractWidget widget && !widget.active && widget.isHovered()) {
                //? if >= 1.21.6 {
                guiGraphics.setTooltipForNextFrame(Component.literal(WWaypointsCompat.DISABLED_REASON), polyplus$rawMouseX, polyplus$rawMouseY);
                //?} else {
                /*((Screen) (Object) this).setTooltipForNextRenderPass(Component.literal(WWaypointsCompat.DISABLED_REASON));
                *///?}
                return;
            }
        }
    }
    //?}
}
