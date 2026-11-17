package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import io.netty.channel.ChannelFuture;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ConnectScreen.class)
public interface ConnectionConnectScreenAccessor {
    @Accessor("parent")
    Screen polyplusParent();

    @Accessor("status")
    Component polyplusStatus();

    @Accessor("connection")
    Connection polyplusConnection();

    @Accessor("channelFuture")
    ChannelFuture polyplusChannelFuture();

    @Accessor("channelFuture")
    void polyplusSetChannelFuture(ChannelFuture future);

    @Accessor("aborted")
    void polyplusSetAborted(boolean aborted);
}
//?}
