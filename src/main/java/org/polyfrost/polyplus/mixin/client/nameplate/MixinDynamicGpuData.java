package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import net.minecraft.client.renderer.DynamicGpuData;
//?} elif >= 1.21.8 {
/*import net.minecraft.client.renderer.DynamicUniforms;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialPipelines;
*///?}
//? if >= 1.21.8 {
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

//? if >= 26.3 {
@Mixin(DynamicGpuData.class)
//?} elif >= 1.21.8 {
/*@Mixin(DynamicUniforms.class)
*///?}
public class MixinDynamicGpuData {
    //? if >= 26.3 {
    @Inject(method = "reset", at = @At("TAIL"))
    private void polyplus$endNameplateFrame(CallbackInfo ci) {
        NameplateMaterialDraw.endFrame();
    }
    //?} elif >= 1.21.8 {
    /*@Inject(method = "reset", at = @At("TAIL"))
    private void polyplus$endNameplateFrame(CallbackInfo ci) {
        NameplateMaterialDraw.endFrame();
        NameplateMaterialPipelines.compileNext();
    }
    *///?}
}
