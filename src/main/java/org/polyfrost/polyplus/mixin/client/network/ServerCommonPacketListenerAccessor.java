package org.polyfrost.polyplus.mixin.client.network;

//? if > 1.8.9 {
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerAccessor {
    @Accessor("connection")
    Connection getPolyplusConnection();
}
//?} else {
/*import net.minecraft.network.Connection;
import net.minecraft.server.network.handler.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayNetworkHandler.class)
public interface ServerCommonPacketListenerAccessor {
    @Accessor("connection")
    Connection getPolyplusConnection();
}
*///?}
