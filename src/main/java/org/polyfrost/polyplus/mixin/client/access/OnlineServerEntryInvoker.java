package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public interface OnlineServerEntryInvoker {
    @Invoker("<init>")
    static ServerSelectionList.OnlineServerEntry polyplus$create(
        ServerSelectionList list,
        JoinMultiplayerScreen screen,
        ServerData data
    ) {
        throw new AssertionError();
    }
}
//?} else {
/*import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ServerListEntryWidget;
import net.minecraft.client.options.ServerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerListEntryWidget.class)
public interface OnlineServerEntryInvoker {
    @Invoker("<init>")
    static ServerListEntryWidget polyplus$create(MultiplayerScreen screen, ServerListEntry data) {
        throw new AssertionError();
    }
}
*///?}
