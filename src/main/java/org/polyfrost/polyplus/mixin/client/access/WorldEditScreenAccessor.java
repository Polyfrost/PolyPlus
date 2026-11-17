package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EditWorldScreen.class)
public interface WorldEditScreenAccessor {
    @Accessor("callback")
    BooleanConsumer polyplusCallback();

    @Accessor("levelAccess")
    LevelStorageSource.LevelStorageAccess polyplusLevelAccess();

    @Accessor("nameEdit")
    EditBox polyplusNameEdit();

    @Invoker("onRename")
    void polyplusOnRename(String name);
}
//?}
