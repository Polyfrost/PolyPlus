package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.renderer.GameRenderer;
//?} else {
/*import net.minecraft.client.render.GameRenderer;
*///?}
import org.spongepowered.asm.mixin.Mixin;

//? if >= 1.21.11 {
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewOffscreen;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

//? if >= 1.21.11 && < 26.3 {
/*import net.minecraft.client.DeltaTracker;
*///?}

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    //? if >= 1.21.11 {
    @Inject(method = "render", at = @At("HEAD"))
    //? if >= 26.3 {
    private void polyplus$renderPlayerPreviews(CallbackInfo ci) {
    //?} else {
    /*private void polyplus$renderPlayerPreviews(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
    *///?}
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        //? if >= 26.2 {
        RenderTarget rt = ((GameRenderer) (Object) this).mainRenderTarget();
        //?} else {
        /*RenderTarget rt = mc.getMainRenderTarget();
        *///?}
        if (rt != null) PlayerPreviewOffscreen.renderAll(rt);
    }
    //?}
}
