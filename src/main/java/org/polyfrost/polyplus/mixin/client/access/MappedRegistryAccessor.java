package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.core.MappedRegistry;
//?} else
//import net.minecraft.util.registry.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
//? if > 1.8.9 {
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
//?}

@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor {
    //? if > 1.8.9 {
    @Accessor("frozen")
    boolean polyplus$isFrozen();

    @Accessor("frozen")
    void polyplus$setFrozen(boolean frozen);

    @Accessor("unregisteredIntrusiveHolders")
    Map<?, ?> polyplus$getIntrusiveHolders();

    @Accessor("unregisteredIntrusiveHolders")
    void polyplus$setIntrusiveHolders(Map<?, ?> holders);
    //?}
}
