package org.polyfrost.polyplus.mixin.client.access;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(AbstractSelectionList.class)
public interface AbstractSelectionListAccessor {
    @Accessor("children")
    List<?> polyplus$children();

    //? if < 1.21.10 {
    /*@Accessor("itemHeight")
    int polyplus$entryHeight();
    *///?} else {
    @Accessor("defaultEntryHeight")
    int polyplus$entryHeight();
    //?}
}
