package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.resource.pack.ResourcePacks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.file.Path;

@Mixin(ResourcePacks.UnopenedPack.class)
public abstract class PackUnopenedPackMixin {
    @Shadow @Final private File file;
    @Shadow @Final ResourcePacks loader;

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void polyplus$nestedName(CallbackInfoReturnable<String> cir) {
        String nested = polyplus$nestedPath();
        if (nested != null) cir.setReturnValue(nested);
    }

    @Inject(method = "toString", at = @At("HEAD"), cancellable = true)
    private void polyplus$nestedKey(CallbackInfoReturnable<String> cir) {
        String nested = polyplus$nestedPath();
        if (nested != null) {
            cir.setReturnValue(String.format("%s:%s:%d", nested, this.file.isDirectory() ? "folder" : "zip", this.file.lastModified()));
        }
    }

    @Unique
    private String polyplus$nestedPath() {
        File root = this.loader.getDirectory();
        if (root == null || this.file == null) return null;
        Path base = root.toPath().toAbsolutePath().normalize();
        Path path = this.file.toPath().toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent == null || parent.equals(base) || !path.startsWith(base)) return null;
        return base.relativize(path).toString().replace('\\', '/');
    }
}
*///?}
