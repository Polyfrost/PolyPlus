package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlayerTabOverlay.class, priority = 1500)
public class MixinPlayerTabOverlay {
    @WrapOperation(
        //? if >= 26.1 {
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        )
        //?} elif >= 1.21.8 {
        /*method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        )
        *///?} else {
        /*method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"
        )
        *///?}
    )
    //? if < 1.21.8 {
    /*private int polyplus$tabBadge(
        GuiGraphics graphics,
        Font font,
        Component name,
        int x,
        int y,
        int color,
        Operation<Integer> original,
        @Local PlayerInfo info
    ) {
        int offset = polyplus$badgeOffset(info);
        if (offset != 0) PolyPlusBadge.blitTab(graphics, x, y);
        return original.call(graphics, font, name, x + offset, y, color);
    }
    *///?} else {
    private void polyplus$tabBadge(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        Font font,
        Component name,
        int x,
        int y,
        int color,
        Operation<Void> original,
        @Local PlayerInfo info
    ) {
        int offset = polyplus$badgeOffset(info);
        if (offset != 0) PolyPlusBadge.blitTab(graphics, x, y);
        original.call(graphics, font, name, x + offset, y, color);
    }
    //?}

    @WrapOperation(
        //? if >= 26.1 {
        method = "extractRenderState",
        //?} else {
        /*method = "render",
        *///?}
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I",
            ordinal = 0
        )
    )
    private int polyplus$tabBadgeWidth(Font font, FormattedText text, Operation<Integer> original, @Local PlayerInfo info) {
        return original.call(font, text) + polyplus$badgeOffset(info);
    }

    @Unique
    private static int polyplus$badgeOffset(PlayerInfo info) {
        return info != null && PolyPlusBadge.shouldBadgeTab(info) ? PolyPlusBadge.BADGE_ADVANCE : 0;
    }
}
