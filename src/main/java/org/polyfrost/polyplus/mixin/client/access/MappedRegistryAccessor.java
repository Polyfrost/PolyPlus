package org.polyfrost.polyplus.mixin.client.access;

import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;

//? if = 1.21.1 {
/*import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
*///?}

@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor<T> {
    //? if < 1.21.4 {
    /*@Accessor("frozen")
    void polyplus$setFrozen(boolean frozen);

    @Accessor("unregisteredIntrusiveHolders")
    Map<T, Holder.Reference<T>> polyplus$getIntrusiveHolders();

    @Accessor("unregisteredIntrusiveHolders")
    void polyplus$setIntrusiveHolders(Map<T, Holder.Reference<T>> holders);
    *///?}
}
