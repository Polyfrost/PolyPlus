package org.polyfrost.polyplus.mixin.client.access;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.world.EditWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditWorldScreen.class)
public interface WorldLegacyEditAccessor {
    @Accessor("parent")
    Screen polyplusParent();

    @Accessor("worldName")
    String polyplusWorldName();
}
*///?}
