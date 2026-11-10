package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.resource.pack.DirectoryResourcePack;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.client.resource.pack.ZippedResourcePack;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

@Mixin(ResourcePacks.UnopenedPack.class)
public abstract class MixinUnopenedPack {
    @Shadow @Final private File file;
    @Shadow @Final ResourcePacks loader;

    @ModifyArg(method = "bindIcon", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/DynamicTexture;<init>(Ljava/awt/image/BufferedImage;)V"))
    private BufferedImage polyplus$defaultIconWhenMissing(BufferedImage icon) {
        if (icon != null) return icon;
        ResourcePack pack = this.file.isDirectory() ? new DirectoryResourcePack(this.file) : new ZippedResourcePack(this.file);
        try {
            icon = pack.getIcon();
        } catch (IOException ignored) {
        } finally {
            if (pack instanceof Closeable) IOUtils.closeQuietly((Closeable) pack);
        }
        if (icon != null) return icon;
        try {
            return this.loader.defaultPack.getIcon();
        } catch (IOException e) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
    }
}
*///?}
