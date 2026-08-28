package org.polyfrost.polyplus.mixin.client;

//? if >= 1.21.11 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.gui.panorama.CustomPanorama;
import org.spongepowered.asm.mixin.injection.At;
//?}
import net.minecraft.client.renderer.CubeMap;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CubeMap.class)
public class MixinCubeMap {
    //? if >= 1.21.11 {
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;"
        )
    )
    private AbstractTexture polyplus$swapPanoramaCubeMap(TextureManager manager, Identifier original, Operation<AbstractTexture> operation) {
        return operation.call(manager, CustomPanorama.cubeMapTexture(original));
    }
    //?}
}
