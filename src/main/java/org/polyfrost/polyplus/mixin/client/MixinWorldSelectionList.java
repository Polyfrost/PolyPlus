package org.polyfrost.polyplus.mixin.client;

//? if >= 26.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.polyfrost.polyplus.client.gui.vanillamenu.WorldSectionHeader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Worlds for this version first, then an "Other versions" section, each under a label row. */
@Mixin(WorldSelectionList.class)
public abstract class MixinWorldSelectionList extends ObjectSelectionList<WorldSelectionList.Entry> {
    private MixinWorldSelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    @ModifyVariable(method = "fillLevels", at = @At("HEAD"), argsOnly = true)
    private List<LevelSummary> polyplus$currentVersionFirst(List<LevelSummary> levels) {
        if (!WorldSectionHeader.enabled()) return levels;
        var sorted = new ArrayList<LevelSummary>(levels.size());
        for (var level : levels) if (!WorldSectionHeader.otherVersion(level)) sorted.add(level);
        for (var level : levels) if (WorldSectionHeader.otherVersion(level)) sorted.add(level);
        return sorted;
    }

    @Inject(method = "fillLevels", at = @At("RETURN"))
    private void polyplus$addSections(String filter, List<LevelSummary> levels, CallbackInfo ci) {
        var selected = getSelected();
        var worlds = new ArrayList<WorldSelectionList.WorldListEntry>();
        boolean hadHeaders = false;
        for (var entry : List.copyOf(children())) {
            if (entry instanceof WorldSelectionList.WorldListEntry world) worlds.add(world);
            else if (entry instanceof WorldSectionHeader) hadHeaders = true;
        }
        if (worlds.isEmpty()) return;
        boolean sections = WorldSectionHeader.enabled();
        boolean anyOther = sections && worlds.stream().anyMatch(w -> WorldSectionHeader.otherVersion(w.getLevelSummary()));
        if (!anyOther && !hadHeaders) return;
        for (var entry : List.copyOf(children())) {
            if (entry instanceof WorldSelectionList.WorldListEntry || entry instanceof WorldSectionHeader) removeEntry(entry);
        }
        boolean anyCurrent = worlds.stream().anyMatch(w -> !WorldSectionHeader.otherVersion(w.getLevelSummary()));
        if (anyOther && anyCurrent) addEntry(new WorldSectionHeader(Component.literal("Playable on this version")), WorldSectionHeader.HEIGHT);
        for (var world : worlds) if (!anyOther || !WorldSectionHeader.otherVersion(world.getLevelSummary())) addEntry(world);
        if (anyOther) {
            addEntry(new WorldSectionHeader(Component.literal("Other versions")), WorldSectionHeader.HEIGHT);
            for (var world : worlds) if (WorldSectionHeader.otherVersion(world.getLevelSummary())) addEntry(world);
        }
        if (selected instanceof WorldSelectionList.WorldListEntry) setSelected(selected);
    }
}
//?}
