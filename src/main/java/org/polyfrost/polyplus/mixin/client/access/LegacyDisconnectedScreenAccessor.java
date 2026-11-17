package org.polyfrost.polyplus.mixin.client.access;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.multiplayer.DisconnectedScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DisconnectedScreen.class)
public interface LegacyDisconnectedScreenAccessor {
    @Accessor("parent")
    Screen polyplusParent();

    @Accessor("title")
    String polyplusTitle();

    @Accessor("reason")
    Text polyplusReason();
}
*///?}
