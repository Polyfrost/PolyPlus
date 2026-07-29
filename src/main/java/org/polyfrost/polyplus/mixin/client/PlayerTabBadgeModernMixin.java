//? if >= 26.1 {
package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlayerTabOverlay.class, priority = 1500)
public class PlayerTabBadgeModernMixin {
    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        )
    )
    private void polyplus$tabBadge(
        GuiGraphicsExtractor graphics,
        Font font,
        Component name,
        int x,
        int y,
        int color,
        Operation<Void> original,
        @Local PlayerInfo info
    ) {
        if (info != null && PolyPlusBadge.shouldBadge(PolyPlusBadge.tabUuid(info.getProfile()))) {
            PolyPlusBadge.blitTab(graphics, x, y);
            original.call(graphics, font, name, x + PolyPlusBadge.BADGE_ADVANCE, y, color);
        } else {
            original.call(graphics, font, name, x, y, color);
        }
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I",
            ordinal = 0
        )
    )
    private int polyplus$tabBadgeWidth(
        Font font,
        FormattedText text,
        Operation<Integer> original,
        @Local PlayerInfo info
    ) {
        int width = original.call(font, text);
        if (info != null && PolyPlusBadge.shouldBadge(PolyPlusBadge.tabUuid(info.getProfile()))) {
            return width + PolyPlusBadge.BADGE_ADVANCE;
        }
        return width;
    }
}
//?}
