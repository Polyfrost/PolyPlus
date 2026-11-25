package org.polyfrost.polyplus.client.featured;

//? if > 1.8.9 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}

//? if < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

public final class FeaturedServerVanillaRenderer {
    private static final int SEGMENTS_X = 12;
    private static final int SEGMENTS_Y = 3;
    private static final int VANILLA_STATUS_RIGHT_GAP = 20;
    private static final int BUTTON_GAP = 5;
    //? if >= 1.21.10 {
    private static final int CONTENT_INSET = 2;
    //?} else {
    /*private static final int CONTENT_INSET = 0;
    *///?}
    private static final int SECTION_INSET = 4;
    private static final int SECTION_LABEL_GAP = 5;
    private static final int CROSS_SIZE = 5;
    private static final int STATUS_ICON_WIDTH = 10;
    private static final int STATUS_ICON_HEIGHT = 8;
    private static final int STATUS_ICON_RIGHT_GAP = 15;
    private static final int STAR_SIZE = 7;
    private static final int STAR_COLOR = 0xFFFFD700;
    private static final byte[] STAR_ROWS = {
        0b0001000,
        0b0011100,
        0b1111111,
        0b0111110,
        0b0011100,
        0b0110110,
        0b1100011,
    };
    private static final int GLYPH_CAP_HEIGHT = 7;
    private static final int FOLDER_ARROW_WIDTH = 12;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int ARROW_HOVER_COLOR = 0xFFFFFFFF;
    private static final int ARROW_DISABLED_COLOR = 0xFF555555;

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
            var label = row.server() == null && row.folder() == null && !"Sponsored".equals(row.data().name)
                ? Component.literal(row.data().name)
                : headerLabel(row);
            var font = Minecraft.getInstance().font;
            int labelWidth = font.width(label);
            int labelX = x + (width - labelWidth) / 2;
            int labelY = y + (height - font.lineHeight) / 2;
            int lineY = labelY + (font.lineHeight - 1) / 2;
            int lineColor = 0xFF666666;
            graphics.fill(
                x + SECTION_INSET,
                lineY,
                Math.max(x + SECTION_INSET, labelX - SECTION_LABEL_GAP),
                lineY + 1,
                lineColor
            );
            boolean arrows = folderArrows(row)
                && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            int lineEnd = x + width - SECTION_INSET - (arrows ? 2 * FOLDER_ARROW_WIDTH + SECTION_LABEL_GAP : 0);
            graphics.fill(
                Math.min(lineEnd, labelX + labelWidth + SECTION_LABEL_GAP),
                lineY,
                lineEnd,
                lineY + 1,
                lineColor
            );
            if (arrows) {
                var shown = FeaturedServerRowRegistry.folders(row.list());
                int index = shown.indexOf(row.folder());
                int arrowX = x + width - SECTION_INSET - 2 * FOLDER_ARROW_WIDTH;
                int hit = folderArrowHit(row, mouseX);
                int upColor = index <= 0 ? ARROW_DISABLED_COLOR : hit < 0 ? ARROW_HOVER_COLOR : LABEL_COLOR;
                int downColor = index < 0 || index >= shown.size() - 1 ? ARROW_DISABLED_COLOR : hit > 0 ? ARROW_HOVER_COLOR : LABEL_COLOR;
                //? if >= 26.1 {
                graphics.text(font, "\u25B2", arrowX + 2, labelY, upColor);
                graphics.text(font, "\u25BC", arrowX + FOLDER_ARROW_WIDTH + 2, labelY, downColor);
                //?} else {
                /*graphics.drawString(font, "\u25B2", arrowX + 2, labelY, upColor);
                graphics.drawString(font, "\u25BC", arrowX + FOLDER_ARROW_WIDTH + 2, labelY, downColor);
                *///?}
            }
            //? if >= 26.1 {
            graphics.text(font, label, labelX, labelY, LABEL_COLOR);
            //?} else {
            /*graphics.drawString(font, label, labelX, labelY, LABEL_COLOR);
            *///?}
            return;
        }
        if (row.server() == null) return; // a recent direct connect row, drawn like any saved server
        drawOutline(graphics, row.server().getOutlineStyle(), x, y, width, height);
    }

    public static Component headerLabel(FeaturedServerRowRegistry.Row row) {
        if (row.folder() == null) return Component.translatable("polyplus.featured.sponsored");
        return Component.literal((ServerFolders.isCollapsed(row.folder()) ? "\u25B6 " : "\u25BC ") + row.folder());
    }

    private static boolean folderArrows(FeaturedServerRowRegistry.Row row) {
        return row.folder() != null && ServerFolders.sort() == ServerFolders.Sort.CUSTOM;
    }

    public static int folderArrowHit(FeaturedServerRowRegistry.Row row, double mouseX) {
        if (!folderArrows(row)) return 0;
        int arrowX = row.x() + row.width() - SECTION_INSET - 2 * FOLDER_ARROW_WIDTH;
        if (mouseX < arrowX || mouseX >= arrowX + 2 * FOLDER_ARROW_WIDTH) return 0;
        return mouseX < arrowX + FOLDER_ARROW_WIDTH ? -1 : 1;
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
        if (row.header() || row.server() == null) return;
        if (row.promoted()) {
            int iconX = row.x() + row.width() - CONTENT_INSET - STATUS_ICON_RIGHT_GAP;
            drawStar(
                graphics,
                iconX + (STATUS_ICON_WIDTH - STAR_SIZE) / 2,
                row.y() + CONTENT_INSET + STATUS_ICON_HEIGHT + 4
            );
        }
        var campaign = row.server().getFeatured();
        boolean dismissible = campaign != null && campaign.getDismissibleInServerList();
        boolean restorable = !row.promoted() && FeaturedServers.isMultiplayerRestorable(row.server());
        if (!(row.promoted() && dismissible) && !restorable) {
            row.dismissBounds(0, 0, 0, 0);
            return;
        }
        var font = Minecraft.getInstance().font;
        var label = restorable ? Component.translatable("polyplus.featured.restore") : null;
        int contentWidth = label != null ? font.width(label) : CROSS_SIZE;
        var status = row.data().state() == ServerData.State.INCOMPATIBLE
            ? row.data().version
            : row.data().status;
        int statusWidth = font.width(status);
        int statusX = row.x() + row.width() - CONTENT_INSET - VANILLA_STATUS_RIGHT_GAP - statusWidth;
        int contentX = statusX - BUTTON_GAP - contentWidth;
        int contentY = row.y() + CONTENT_INSET + 1;
        row.dismissBounds(contentX - 2, contentY - 2, contentWidth + 4, 11);
        int color = row.dismissHit(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFAAAAAA;
        if (label == null) {
            drawCross(graphics, contentX, contentY + (GLYPH_CAP_HEIGHT - CROSS_SIZE) / 2, color);
            return;
        }
        //? if >= 26.1 {
        graphics.text(font, label, contentX, contentY, color);
        //?} else {
        /*graphics.drawString(font, label, contentX, contentY, color);
        *///?}
    }

    private static void drawStar(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int x, int y
    ) {
        for (int row = 0; row < STAR_ROWS.length; row++) {
            int bits = STAR_ROWS[row];
            for (int col = 0; col < STAR_SIZE; col++) {
                if ((bits & (1 << (STAR_SIZE - 1 - col))) == 0) continue;
                graphics.fill(x + col, y + row, x + col + 1, y + row + 1, STAR_COLOR);
            }
        }
    }

    private static void drawCross(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int x, int y, int color
    ) {
        for (int i = 0; i < CROSS_SIZE; i++) {
            graphics.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            graphics.fill(x + CROSS_SIZE - 1 - i, y + i, x + CROSS_SIZE - i, y + i + 1, color);
        }
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
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;

public final class FeaturedServerVanillaRenderer {
    private static final int SEGMENTS_X = 12;
    private static final int SEGMENTS_Y = 3;
    private static final int VANILLA_STATUS_RIGHT_GAP = 17;
    private static final int BUTTON_GAP = 5;
    private static final int SECTION_INSET = 4;
    private static final int SECTION_LABEL_GAP = 5;
    private static final int CROSS_SIZE = 5;
    private static final int STATUS_ICON_WIDTH = 10;
    private static final int STATUS_ICON_HEIGHT = 8;
    private static final int STATUS_ICON_RIGHT_GAP = 15;
    private static final int STAR_SIZE = 7;
    private static final int STAR_COLOR = 0xFFFFD700;
    private static final byte[] STAR_ROWS = {
        0b0001000,
        0b0011100,
        0b1111111,
        0b0111110,
        0b0011100,
        0b0110110,
        0b1100011,
    };
    private static final int GLYPH_CAP_HEIGHT = 7;
    private static final int FOLDER_ARROW_WIDTH = 12;
    private static final int TRIANGLE_SIZE = 7;
    private static final int TRIANGLE_GAP = 4;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int LINE_COLOR = 0xFF666666;
    private static final int ARROW_HOVER_COLOR = 0xFFFFFFFF;
    private static final int ARROW_DISABLED_COLOR = 0xFF555555;

    private FeaturedServerVanillaRenderer() {
    }

    public static void folderHeader(
        FeaturedServerRowRegistry.FolderHeader header,
        int x, int y, int width, int height, int mouseX, boolean hovered
    ) {
        var font = Minecraft.getInstance().textRenderer;
        String label = header.folder();
        int labelWidth = font.getWidth(label);
        int contentWidth = TRIANGLE_SIZE + TRIANGLE_GAP + labelWidth;
        int contentX = x + (width - contentWidth) / 2;
        int labelY = y + (height - font.fontHeight) / 2;
        int lineY = labelY + (font.fontHeight - 1) / 2;
        GuiElement.fill(x + SECTION_INSET, lineY, Math.max(x + SECTION_INSET, contentX - SECTION_LABEL_GAP), lineY + 1, LINE_COLOR);
        boolean arrows = hovered && folderArrows();
        int lineEnd = x + width - SECTION_INSET - (arrows ? 2 * FOLDER_ARROW_WIDTH + SECTION_LABEL_GAP : 0);
        GuiElement.fill(Math.min(lineEnd, contentX + contentWidth + SECTION_LABEL_GAP), lineY, lineEnd, lineY + 1, LINE_COLOR);
        if (ServerFolders.isCollapsed(header.folder())) {
            triangleRight(contentX + 2, labelY, LABEL_COLOR);
        } else {
            triangleDown(contentX, labelY + 2, LABEL_COLOR);
        }
        font.draw(label, contentX + TRIANGLE_SIZE + TRIANGLE_GAP, labelY, LABEL_COLOR);
        if (!arrows) return;
        var shown = ((FeaturedServerListAccess) header.list()).polyplus$folders();
        int index = shown.indexOf(header.folder());
        int arrowX = x + width - SECTION_INSET - 2 * FOLDER_ARROW_WIDTH;
        int hit = folderArrowHit(header, mouseX);
        int upColor = index <= 0 ? ARROW_DISABLED_COLOR : hit < 0 ? ARROW_HOVER_COLOR : LABEL_COLOR;
        int downColor = index < 0 || index >= shown.size() - 1 ? ARROW_DISABLED_COLOR : hit > 0 ? ARROW_HOVER_COLOR : LABEL_COLOR;
        triangleUp(arrowX + 2, labelY + 2, upColor);
        triangleDown(arrowX + FOLDER_ARROW_WIDTH + 2, labelY + 2, downColor);
    }

    private static boolean folderArrows() {
        return ServerFolders.sort() == ServerFolders.Sort.CUSTOM;
    }

    public static int folderArrowHit(FeaturedServerRowRegistry.FolderHeader header, int mouseX) {
        if (!folderArrows()) return 0;
        int arrowX = header.x() + header.width() - SECTION_INSET - 2 * FOLDER_ARROW_WIDTH;
        if (mouseX < arrowX || mouseX >= arrowX + 2 * FOLDER_ARROW_WIDTH) return 0;
        return mouseX < arrowX + FOLDER_ARROW_WIDTH ? -1 : 1;
    }

    public static void triangleDown(int x, int y, int color) {
        for (int row = 0; row < (TRIANGLE_SIZE + 1) / 2; row++) {
            GuiElement.fill(x + row, y + row, x + TRIANGLE_SIZE - row, y + row + 1, color);
        }
    }

    public static void triangleUp(int x, int y, int color) {
        int rows = (TRIANGLE_SIZE + 1) / 2;
        for (int row = 0; row < rows; row++) {
            GuiElement.fill(x + row, y + rows - 1 - row, x + TRIANGLE_SIZE - row, y + rows - row, color);
        }
    }

    public static void triangleRight(int x, int y, int color) {
        for (int col = 0; col < (TRIANGLE_SIZE + 1) / 2; col++) {
            GuiElement.fill(x + col, y + col, x + col + 1, y + TRIANGLE_SIZE - col, color);
        }
    }

    public static void header(int x, int y, int width, int height) {
        var font = Minecraft.getInstance().textRenderer;
        String label = I18n.translate("polyplus.featured.sponsored");
        int labelWidth = font.getWidth(label);
        int labelX = x + (width - labelWidth) / 2;
        int labelY = y + (height - font.fontHeight) / 2;
        int lineY = labelY + (font.fontHeight - 1) / 2;
        int lineColor = 0xFF666666;
        GuiElement.fill(x + SECTION_INSET, lineY, Math.max(x + SECTION_INSET, labelX - SECTION_LABEL_GAP), lineY + 1, lineColor);
        GuiElement.fill(
            Math.min(x + width - SECTION_INSET, labelX + labelWidth + SECTION_LABEL_GAP),
            lineY,
            x + width - SECTION_INSET,
            lineY + 1,
            lineColor
        );
        font.draw(label, labelX, labelY, 0xFFAAAAAA);
    }

    public static void before(FeaturedServerRowRegistry.Row row, int x, int y, int width, int height) {
        row.bounds(x, y, width);
        drawOutline(row.server().getOutlineStyle(), x - 2, y - 2, width + 4, height + 4);
    }

    public static void after(FeaturedServerRowRegistry.Row row, int mouseX, int mouseY) {
        if (row.promoted()) {
            int iconX = row.x() + row.width() - STATUS_ICON_RIGHT_GAP;
            drawStar(iconX + (STATUS_ICON_WIDTH - STAR_SIZE) / 2, row.y() + STATUS_ICON_HEIGHT + 4);
        }
        var campaign = row.server().getFeatured();
        boolean dismissible = campaign != null && campaign.getDismissibleInServerList();
        boolean restorable = !row.promoted() && FeaturedServers.isMultiplayerRestorable(row.server());
        if (!(row.promoted() && dismissible) && !restorable) {
            row.dismissBounds(0, 0, 0, 0);
            return;
        }
        var font = Minecraft.getInstance().textRenderer;
        String label = restorable ? I18n.translate("polyplus.featured.restore") : null;
        int contentWidth = label != null ? font.getWidth(label) : CROSS_SIZE;
        var data = row.data();
        boolean incompatible = data.protocol != 47;
        String status = incompatible ? Formatting.DARK_RED + data.version : data.status;
        int statusWidth = font.getWidth(status == null ? "" : status);
        int statusX = row.x() + row.width() - VANILLA_STATUS_RIGHT_GAP - statusWidth;
        int contentX = statusX - BUTTON_GAP - contentWidth;
        int contentY = row.y() + 1;
        row.dismissBounds(contentX - 2, contentY - 2, contentWidth + 4, 11);
        int color = row.dismissHit(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFAAAAAA;
        if (label == null) {
            drawCross(contentX, contentY + (GLYPH_CAP_HEIGHT - CROSS_SIZE) / 2, color);
            return;
        }
        font.draw(label, contentX, contentY, color);
    }

    private static void drawStar(int x, int y) {
        for (int row = 0; row < STAR_ROWS.length; row++) {
            int bits = STAR_ROWS[row];
            for (int col = 0; col < STAR_SIZE; col++) {
                if ((bits & (1 << (STAR_SIZE - 1 - col))) == 0) continue;
                GuiElement.fill(x + col, y + row, x + col + 1, y + row + 1, STAR_COLOR);
            }
        }
    }

    private static void drawCross(int x, int y, int color) {
        for (int i = 0; i < CROSS_SIZE; i++) {
            GuiElement.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            GuiElement.fill(x + CROSS_SIZE - 1 - i, y + i, x + CROSS_SIZE - i, y + i + 1, color);
        }
    }

    private static void drawOutline(OutlineStyle style, int x, int y, int width, int height) {
        if (style instanceof OutlineStyle.None) return;
        if (!(style instanceof OutlineStyle.Rainbow)) {
            int color = FeaturedServerColors.INSTANCE.colorAt(style, 0f, 0L);
            GuiElement.fill(x, y, x + width, y + 1, color);
            GuiElement.fill(x, y + height - 1, x + width, y + height, color);
            GuiElement.fill(x, y, x + 1, y + height, color);
            GuiElement.fill(x + width - 1, y, x + width, y + height, color);
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        int perimeter = Math.max(1, 2 * (width + height));
        horizontal(x, y, width, SEGMENTS_X, 0, perimeter, now, false);
        vertical(x + width - 1, y, height, SEGMENTS_Y, width, perimeter, now, false);
        horizontal(x, y + height - 1, width, SEGMENTS_X, width + height, perimeter, now, true);
        vertical(x, y, height, SEGMENTS_Y, 2 * width + height, perimeter, now, true);
    }

    private static void horizontal(int x, int y, int length, int segments, int distance, int perimeter, long now, boolean reverse) {
        for (int i = 0; i < segments; i++) {
            int start = i * length / segments;
            int end = (i + 1) * length / segments;
            int along = reverse ? length - start : start;
            int color = FeaturedServerColors.INSTANCE.rainbowAt((distance + along) / (float) perimeter, now);
            GuiElement.fill(x + start, y, x + Math.max(start + 1, end), y + 1, color);
        }
    }

    private static void vertical(int x, int y, int length, int segments, int distance, int perimeter, long now, boolean reverse) {
        for (int i = 0; i < segments; i++) {
            int start = i * length / segments;
            int end = (i + 1) * length / segments;
            int along = reverse ? length - start : start;
            int color = FeaturedServerColors.INSTANCE.rainbowAt((distance + along) / (float) perimeter, now);
            GuiElement.fill(x, y + start, x + 1, y + Math.max(start + 1, end), color);
        }
    }
}
*///?}
