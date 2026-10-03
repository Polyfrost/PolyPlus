package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientHandshakePacketListenerImpl.class)
public interface ClientHandshakePacketListenerAccessor {
    @Accessor("connection")
    Connection polyplus$getConnection();
}
//?} else {
/*import net.minecraft.client.network.handler.ClientLoginNetworkHandler;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLoginNetworkHandler.class)
public interface ClientHandshakePacketListenerAccessor {
    @Accessor("connection")
    Connection polyplus$getConnection();
}
*///?}
