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

    @Accessor("worldName")
    void polyplusSetWorldName(String value);

    @Accessor("seed")
    void polyplusSetSeed(String value);

    @Accessor("gameModeName")
    void polyplusSetGameModeName(String value);

    @Accessor("structures")
    void polyplusSetStructures(boolean value);

    @Accessor("allowCommands")
    void polyplusSetAllowCommands(boolean value);

    @Accessor("cheatsEnabled")
    void polyplusSetCheatsEnabled(boolean value);

    @Accessor("bonusChest")
    void polyplusSetBonusChest(boolean value);

    @Accessor("hardcore")
    void polyplusSetHardcore(boolean value);

    @Accessor("generatorType")
    void polyplusSetGeneratorType(int value);
}
*///?}
