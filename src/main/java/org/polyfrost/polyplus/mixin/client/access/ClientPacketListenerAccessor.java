package org.polyfrost.polyplus.mixin.client.access;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
    @Mutable
    @Accessor("registryAccess")
    void polyplus$setRegistryAccess(RegistryAccess.Frozen registryAccess);
}
