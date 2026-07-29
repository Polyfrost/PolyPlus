//? if >= 1.21.11 {
package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.resources.Identifier;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.client.gui.panorama.CustomPanorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >= 26.1 {
@Mixin(net.minecraft.client.renderer.Panorama.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.PanoramaRenderer.class)
*///?}
public class Mixin_CustomPanoramaOverlay {
    @ModifyExpressionValue(
        //? if >= 26.1 {
        method = "extractRenderState",
        //?} else {
        /*method = "render",
        *///?}
        at = @At(
            value = "FIELD",
            //? if >= 26.1 {
            target = "Lnet/minecraft/client/renderer/Panorama;PANORAMA_OVERLAY:Lnet/minecraft/resources/Identifier;",
            //?} else {
            /*target = "Lnet/minecraft/client/renderer/PanoramaRenderer;PANORAMA_OVERLAY:Lnet/minecraft/resources/Identifier;",
            *///?}
            opcode = Opcodes.GETSTATIC
        )
    )
    private Identifier polyplus$swapPanoramaOverlay(Identifier original) {
        return CustomPanorama.overlayTexture(original);
    }
}
//?}
