//? if >= 1.21.11 && < 26.1 {
/*package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabBadgeMixin {
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        )
    )
    private void polyplus$tabBadge(
        GuiGraphics graphics,
        Font font,
        Component name,
        int x,
        int y,
        int color,
        Operation<Void> original,
        @Local PlayerInfo info
    ) {
        if (info != null && PolyPlusBadge.shouldBadge(info.getProfile().id())) {
            PolyPlusBadge.blitTab(graphics, x, y);
            original.call(graphics, font, name, x + PolyPlusBadge.BADGE_ADVANCE, y, color);
        } else {
            original.call(graphics, font, name, x, y, color);
        }
    }
}
*///?}
