package org.polyfrost.polyplus.mixin.client.nameplate.render;

//? if > 1.8.9 {
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >= 1.21.10 {
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticRenderer;
import org.polyfrost.polyplus.client.nameplate.render.NameTagCanvas;
//?} else {
/*import org.polyfrost.polyplus.client.nameplate.render.NameTags;
*///?}
//? if >= 26.3 {
import net.minecraft.client.renderer.DynamicGpuData;
//?} elif >= 1.21.8 {
/*import net.minecraft.client.renderer.DynamicUniforms;
*///?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
*///?}

//? if >= 26.3 {
@Mixin(DynamicGpuData.class)
//?} elif >= 1.21.8 {
/*@Mixin(DynamicUniforms.class)
*///?} else {
/*@Mixin(RenderSystem.class)
*///?}
public class MixinNameTagFrameEnd {
    //? if >= 1.21.10 {
    @Inject(method = "reset", at = @At("TAIL"))
    private void polyplus$endNameTagFrame(CallbackInfo ci) {
        NameTagCanvas.endFrame();
        CosmeticRenderer.endFrame();
    }
    //?} elif >= 1.21.8 {
    /*@Inject(method = "reset", at = @At("TAIL"))
    private void polyplus$endNameTagFrame(CallbackInfo ci) {
        NameTags.endFrame();
    }
    *///?} else {
    /*@Inject(method = "flipFrame", at = @At("TAIL"))
    private static void polyplus$endNameTagFrame(CallbackInfo ci) {
        NameTags.endFrame();
    }
    *///?}
}
//?}
