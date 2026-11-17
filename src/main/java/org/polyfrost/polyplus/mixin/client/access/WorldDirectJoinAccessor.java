package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DirectJoinServerScreen.class)
public interface WorldDirectJoinAccessor {
    @Accessor("callback")
    BooleanConsumer polyplusCallback();

    @Accessor("serverData")
    ServerData polyplusServerData();
}
//?}
