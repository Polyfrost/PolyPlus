//? if >= 1.21.11 {
package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.gui.panorama.CustomPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CubeMap.class)
public class Mixin_CustomPanoramaCubeMap {
    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;"
        ),
        index = 0
    )
    private Identifier polyplus$swapPanoramaCubeMap(Identifier original) {
        return CustomPanorama.cubeMapTexture(original);
    }
}
//?}
