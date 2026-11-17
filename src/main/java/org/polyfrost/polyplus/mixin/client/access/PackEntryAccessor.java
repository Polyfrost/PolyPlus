package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PackSelectionModel.EntryBase.class)
public interface PackEntryAccessor {
    @Accessor("pack")
    Pack polyplus$pack();
}
//?}
