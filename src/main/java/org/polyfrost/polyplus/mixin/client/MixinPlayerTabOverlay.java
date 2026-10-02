package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}

//? if < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

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
    //? if >= 26.1 {
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
        int offset = polyplus$badgeOffset(info);
        if (offset != 0) PolyPlusBadge.blitTab(graphics, x, y);
        original.call(graphics, font, name, x + offset, y, color);
    }
    //?} elif >= 1.21.8 {
    /*private void polyplus$tabBadge(
        GuiGraphics graphics,
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
    *///?} else {
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
    *///?}

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
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Arrays;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlayerTabOverlay.class, priority = 1500)
public class MixinPlayerTabOverlay {
    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;getWidth(Ljava/lang/String;)I", ordinal = 0)
    )
    private int polyplus$tabBadgeWidth(TextRenderer font, String text, Operation<Integer> original, @Local PlayerInfo info) {
        return original.call(font, text) + polyplus$badgeOffset(info);
    }

    @WrapOperation(
        method = "render",
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I", ordinal = 1),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I", ordinal = 2)
        }
    )
    private int polyplus$tabBadge(TextRenderer font, String text, float x, float y, int color, Operation<Integer> original, @Local PlayerInfo info) {
        int offset = polyplus$badgeOffset(info);
        if (offset != 0) polyplus$queueBadge((int) x, (int) y);
        return original.call(font, text, x + offset, y, color);
    }

    // argentum batches the backgrounds until render returns, so draw badges after its wrapper
    @WrapMethod(method = "render", order = 2000)
    private void polyplus$drawBadges(int width, Scoreboard scoreboard, ScoreboardObjective objective, Operation<Void> original) {
        polyplus$badgeCount = 0;
        try {
            original.call(width, scoreboard, objective);
        } finally {
            for (int i = 0; i < polyplus$badgeCount; i++) {
                PolyPlusBadge.blitTab(polyplus$badges[i * 2], polyplus$badges[i * 2 + 1]);
            }
            polyplus$badgeCount = 0;
        }
    }

    @Unique
    private int[] polyplus$badges = new int[32];

    @Unique
    private int polyplus$badgeCount;

    @Unique
    private void polyplus$queueBadge(int x, int y) {
        int index = polyplus$badgeCount++ * 2;
        if (index + 1 >= polyplus$badges.length) polyplus$badges = Arrays.copyOf(polyplus$badges, polyplus$badges.length * 2);
        polyplus$badges[index] = x;
        polyplus$badges[index + 1] = y;
    }

    @Unique
    private static int polyplus$badgeOffset(PlayerInfo info) {
        return info != null && PolyPlusBadge.shouldBadgeTab(info) ? PolyPlusBadge.BADGE_ADVANCE : 0;
    }
}
*///?}
