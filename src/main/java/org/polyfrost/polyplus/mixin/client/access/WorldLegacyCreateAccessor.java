package org.polyfrost.polyplus.mixin.client.access;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreateWorldScreen.class)
public interface WorldLegacyCreateAccessor {
    @Accessor("parent")
    Screen polyplusParent();

    @Accessor("worldName")
    String polyplusWorldName();

    @Accessor("seed")
    String polyplusSeed();

    @Accessor("gameModeName")
    String polyplusGameModeName();

    @Accessor("structures")
    boolean polyplusStructures();

    @Accessor("allowCommands")
    boolean polyplusAllowCommands();

    @Accessor("generatorType")
    int polyplusGeneratorType();
}
*///?}
