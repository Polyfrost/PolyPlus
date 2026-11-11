package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.1 {
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//?}

//? if >= 1.21.1 {
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("originalImage")
    NativeImage polyplus$originalImage();
}
//?} else {
/*public interface SpriteContentsAccessor {
}
*///?}
