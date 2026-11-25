package org.polyfrost.polyplus.client.gui.vanillamenu;

//? if >= 26.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig;

/** A label row splitting the vanilla world list into this version's worlds and other versions' worlds. */
public final class WorldSectionHeader extends WorldSelectionList.Entry {
    public static final int HEIGHT = 16;
    private static final int LINE_COLOR = 0xFF666666;
    private static final int TEXT_COLOR = 0xFFAAAAAA;
    private static final int INSET = 4;
    private static final int GAP = 6;

    private final Component label;

    public WorldSectionHeader(Component label) {
        this.label = label;
    }

    public static boolean enabled() {
        return PolyPlusMainMenuConfig.getWorldVersionSections();
    }

    /** Saved in another Minecraft version (by data version), so it goes in "Other versions" and asks before opening. */
    public static boolean otherVersion(LevelSummary summary) {
        if (summary instanceof LevelSummary.CorruptedLevelSummary || summary instanceof LevelSummary.SymlinkLevelSummary) return false;
        return summary.levelVersion().minecraftVersion().version() != SharedConstants.getCurrentVersion().dataVersion().version();
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
        var font = Minecraft.getInstance().font;
        int x = getContentX();
        int width = getContentWidth();
        int labelWidth = font.width(label);
        int labelX = x + (width - labelWidth) / 2;
        int labelY = getContentY() + (getContentHeight() - font.lineHeight) / 2 + 1;
        int lineY = labelY + (font.lineHeight - 1) / 2;
        graphics.fill(x + INSET, lineY, Math.max(x + INSET, labelX - GAP), lineY + 1, LINE_COLOR);
        graphics.fill(Math.min(x + width - INSET, labelX + labelWidth + GAP), lineY, x + width - INSET, lineY + 1, LINE_COLOR);
        graphics.text(font, label, labelX, labelY, TEXT_COLOR);
    }

    @Override
    public Component getNarration() {
        return label;
    }
}
//?}
