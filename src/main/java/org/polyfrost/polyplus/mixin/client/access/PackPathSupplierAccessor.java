package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.packs.PathPackResources$PathResourcesSupplier")
public interface PackPathSupplierAccessor {
    @Accessor("content")
    Path polyplus$content();
}
//?}
