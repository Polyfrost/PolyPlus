package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PackSelectionModel.class)
public interface PackModelAccessor {
    @Accessor("repository")
    PackRepository polyplus$repository();

    @Accessor("output")
    Consumer<PackRepository> polyplus$output();

    //? if >= 1.21.10 {
    @Mutable
    @Accessor("onListChanged")
    void polyplus$setOnListChanged(Consumer<PackSelectionModel.EntryBase> callback);
    //?} else {
    /*@Mutable
    @Accessor("onListChanged")
    void polyplus$setOnListChanged(Runnable callback);
    *///?}

    @Invoker("updateRepoSelectedList")
    void polyplus$updateRepoSelectedList();
}
//?}
