//? if < 1.21.4 {
/*package org.polyfrost.polyplus.mixin.client;

import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MappedRegistry.class)
public interface Mixin_UnfreezeRegistry<T> {
    @Accessor("frozen")
    void polyplus$setFrozen(boolean frozen);

    @Accessor("unregisteredIntrusiveHolders")
    Map<T, Holder.Reference<T>> polyplus$getIntrusiveHolders();

    @Accessor("unregisteredIntrusiveHolders")
    void polyplus$setIntrusiveHolders(Map<T, Holder.Reference<T>> holders);
}
*///?}
