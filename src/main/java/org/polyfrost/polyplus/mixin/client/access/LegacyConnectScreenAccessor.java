package org.polyfrost.polyplus.mixin.client.access;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.multiplayer.ConnectScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ConnectScreen.class)
public interface LegacyConnectScreenAccessor {
    @Accessor("parent")
    Screen polyplusParent();

    @Accessor("connection")
    Connection polyplusConnection();

    @Accessor("aborted")
    void polyplusSetAborted(boolean aborted);
}
*///?}
