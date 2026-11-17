package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PackSelectionModel.class)
public interface PackModelAccessor {
    @Accessor("repository")
    PackRepository polyplus$repository();

    @Accessor("output")
    Consumer<PackRepository> polyplus$output();

    @Invoker("updateRepoSelectedList")
    void polyplus$updateRepoSelectedList();
}
//?}
