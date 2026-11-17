package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.packs.FilePackResources$FileResourcesSupplier")
public interface PackFileSupplierAccessor {
    @Accessor("content")
    File polyplus$content();
}
//?}
