package org.polyfrost.polyplus.mixin.client.access;

import java.net.SocketAddress;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("address")
    void polyplus$setAddress(SocketAddress address);
}
