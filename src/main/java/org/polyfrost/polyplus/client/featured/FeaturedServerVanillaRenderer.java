package org.polyfrost.polyplus.client.featured;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

public final class FeaturedServerVanillaRenderer {
    private static final int SEGMENTS_X = 12;
    private static final int SEGMENTS_Y = 3;
    private static final int VANILLA_STATUS_RIGHT_PADDING = 25;
    private static final int SECTION_INSET = 4;
    private static final int SECTION_LABEL_GAP = 5;

    private FeaturedServerVanillaRenderer() {
    }

    public static void before(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        FeaturedServerRowRegistry.Row row,
        int x,
        int y,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        row.bounds(x, y, width, height);
        if (row.header()) {
            var label = Component.translatable("polyplus.featured.sponsored.category", row.expanded() ? "▼" : "▶");
            var font = Minecraft.getInstance().font;
            int labelWidth = font.width(label);
            int labelX = x + (width - labelWidth) / 2;
            int labelY = y + (height - font.lineHeight) / 2;
            int lineY = labelY + (font.lineHeight - 1) / 2;
            int lineColor = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
                ? 0xFFAAAAAA
                : 0xFF666666;
            graphics.fill(
                x + SECTION_INSET,
                lineY,
                Math.max(x + SECTION_INSET, labelX - SECTION_LABEL_GAP),
                lineY + 1,
                lineColor
            );
            graphics.fill(
                Math.min(x + width - SECTION_INSET, labelX + labelWidth + SECTION_LABEL_GAP),
                lineY,
                x + width - SECTION_INSET,
                lineY + 1,
                lineColor
            );
            //? if >= 26.1 {
            graphics.text(font, label, labelX, labelY, 0xFFAAAAAA);
            //?} else {
            /*graphics.drawString(font, label, labelX, labelY, 0xFFAAAAAA);
            *///?}
            return;
        }
        drawOutline(graphics, row.server().getOutlineStyle(), x, y, width, height);
    }

    public static void after(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        FeaturedServerRowRegistry.Row row,
        int mouseX,
        int mouseY
    ) {
        if (!row.promoted() || row.header()) return;
        var font = Minecraft.getInstance().font;
        var label = Component.translatable("polyplus.featured.dismiss");
        int textWidth = font.width(label);
        var status = row.data().state() == ServerData.State.INCOMPATIBLE
            ? row.data().version
            : row.data().status;
        int statusWidth = font.width(status);
        int textX = row.x() + row.width() - VANILLA_STATUS_RIGHT_PADDING - statusWidth - textWidth;
        int textY = row.y() + 3;
        row.dismissBounds(textX - 2, textY - 2, textWidth + 4, 13);
        int color = row.dismissHit(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFAAAAAA;
        //? if >= 26.1 {
        graphics.text(font, label, textX, textY, color);
        //?} else {
        /*graphics.drawString(font, label, textX, textY, color);
        *///?}
    }

    private static void drawOutline(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        OutlineStyle style,
        int x,
        int y,
        int width,
        int height
    ) {
        if (style instanceof OutlineStyle.None) return;
        if (!(style instanceof OutlineStyle.Rainbow)) {
            int color = FeaturedServerColors.INSTANCE.colorAt(style, 0f, 0L);
            solid(graphics, x, y, width, height, color);
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        int perimeter = Math.max(1, 2 * (width + height));
        horizontal(graphics, x, y, width, SEGMENTS_X, 0, perimeter, now, false);
        vertical(graphics, x + width - 1, y, height, SEGMENTS_Y, width, perimeter, now, false);
        horizontal(graphics, x, y + height - 1, width, SEGMENTS_X, width + height, perimeter, now, true);
        vertical(graphics, x, y, height, SEGMENTS_Y, 2 * width + height, perimeter, now, true);
    }

    private static void solid(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int x, int y, int width, int height, int color
    ) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static void horizontal(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int x, int y, int length, int segments, int distance, int perimeter, long now, boolean reverse
    ) {
        for (int i = 0; i < segments; i++) {
            int start = i * length / segments;
            int end = (i + 1) * length / segments;
            int along = reverse ? length - start : start;
            int color = FeaturedServerColors.INSTANCE.rainbowAt((distance + along) / (float) perimeter, now);
            graphics.fill(x + start, y, x + Math.max(start + 1, end), y + 1, color);
        }
    }

    private static void vertical(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int x, int y, int length, int segments, int distance, int perimeter, long now, boolean reverse
    ) {
        for (int i = 0; i < segments; i++) {
            int start = i * length / segments;
            int end = (i + 1) * length / segments;
            int along = reverse ? length - start : start;
            int color = FeaturedServerColors.INSTANCE.rainbowAt((distance + along) / (float) perimeter, now);
            graphics.fill(x, y + start, x + 1, y + Math.max(start + 1, end), color);
        }
    }

}
