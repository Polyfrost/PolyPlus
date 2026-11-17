package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel$EntryBase")
public interface PackEntryAccessor {
    @Accessor("pack")
    Pack polyplus$pack();
}
//?}
