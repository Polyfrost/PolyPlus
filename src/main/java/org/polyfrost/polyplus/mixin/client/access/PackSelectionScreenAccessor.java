package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PackSelectionScreen.class)
public interface PackSelectionScreenAccessor {
    @Accessor("model")
    PackSelectionModel polyplus$model();

    @Accessor("packDir")
    Path polyplus$packDir();

    @Accessor("ticksToReload")
    long polyplus$ticksToReload();

    @Invoker("reload")
    void polyplus$reload();

    @Invoker("closeWatcher")
    void polyplus$closeWatcher();

    @Invoker("copyPacks")
    static void polyplus$copyPacks(Minecraft minecraft, List<Path> files, Path targetDir) {
        throw new AssertionError();
    }
}
//?}
