package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.1 && < 1.21.10 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import org.polyfrost.polyplus.client.nameplate.render.NameplateBufferAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
//? if >= 1.21.1 && < 1.21.8 {
/*import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialDraw;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialPipelines;
*///?}

//? if >= 1.21.1 && < 1.21.10 {
/*@Mixin(RenderSystem.class)
*///?}
public class MixinRenderSystem {
    //? if = 1.21.1 {
    /*@Inject(method = "flipFrame", at = @At("TAIL"))
    private static void polyplus$endNameplateFrame(long window, CallbackInfo ci) {
        NameplateBufferAdapter.endFrame();
    }
    *///?} elif >= 1.21.4 && < 1.21.10 {
    /*@Inject(method = "flipFrame", at = @At("TAIL"))
    private static void polyplus$endNameplateFrame(CallbackInfo ci) {
        NameplateBufferAdapter.endFrame();
    }
    *///?}

    //? if >= 1.21.1 && < 1.21.8 {
    /*@Inject(method = "flipFrame", at = @At("TAIL"))
    private static void polyplus$endNameplateMaterialFrame(CallbackInfo ci) {
        NameplateMaterialDraw.endFrame();
        NameplateMaterialPipelines.compileNext();
    }
    *///?}
}
