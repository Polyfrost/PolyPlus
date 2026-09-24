package org.polyfrost.polyplus.mixin.client.access;

import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor {
    @Accessor("frozen")
    boolean polyplus$isFrozen();

    @Accessor("frozen")
    void polyplus$setFrozen(boolean frozen);

    @Accessor("unregisteredIntrusiveHolders")
    Map<?, ?> polyplus$getIntrusiveHolders();

    @Accessor("unregisteredIntrusiveHolders")
    void polyplus$setIntrusiveHolders(Map<?, ?> holders);
}
