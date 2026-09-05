package org.polyfrost.polyplus.mixin.client.access;

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
