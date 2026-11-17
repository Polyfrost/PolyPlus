package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Pack.class)
public interface PackResourcesAccessor {
    @Accessor("resources")
    Pack.ResourcesSupplier polyplus$resources();
}
//?}
