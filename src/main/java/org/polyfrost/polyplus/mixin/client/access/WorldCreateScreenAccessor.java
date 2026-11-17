package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
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

    @Invoker("openExperimentsScreen")
    void polyplusOpenExperiments(WorldDataConfiguration configuration);
}
//?}
