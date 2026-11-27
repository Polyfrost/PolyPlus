package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreateWorldScreen.class)
public interface WorldCreateScreenAccessor {
    @Accessor("recreated")
    boolean polyplusRecreated();

    @Invoker("onCreate")
    void polyplusOnCreate();

    @Invoker("openDataPackSelectionScreen")
    void polyplusOpenDataPacks(WorldDataConfiguration configuration);

    @Invoker("getDataPackSelectionSettings")
    Pair<Path, PackRepository> polyplusDataPackSettings(WorldDataConfiguration configuration);

    @Invoker("tryApplyNewDataPacks")
    void polyplusTryApplyNewDataPacks(PackRepository repository, boolean dataPackScreen, Consumer<WorldDataConfiguration> onAbort);
}
//?}
