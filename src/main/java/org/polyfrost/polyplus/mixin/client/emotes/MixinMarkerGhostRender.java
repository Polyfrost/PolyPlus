package org.polyfrost.polyplus.mixin.client.emotes;

//? if > 1.8.9 {
//? if >= 26.1 {
import net.minecraft.client.DeltaTracker;
//?}
import net.minecraft.client.renderer.GameRenderer;
import org.polyfrost.polyplus.client.emotes.duo.MarkerGhost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinMarkerGhostRender {
    //? if >= 26.1 {
    @Inject(method = "extract", at = @At("RETURN"))
    private void polyplus$extractMarkerGhost(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (advanceGameTime) MarkerGhost.extract((GameRenderer) (Object) this);
    }
    //?}

    //? if >= 26.2 {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;render", shift = At.Shift.AFTER), require = 0)
    //?} else {
    /*@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel", shift = At.Shift.AFTER), require = 0)
    *///?}
    private void polyplus$drawMarkerGhost(CallbackInfo ci) {
        MarkerGhost.draw((GameRenderer) (Object) this);
    }
}
//?} else {
/*import net.minecraft.client.render.GameRenderer;
import org.polyfrost.polyplus.client.emotes.duo.MarkerGhost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinMarkerGhostRender {
    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;clear(I)V", ordinal = 1), require = 0)
    private void polyplus$drawMarkerGhost(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        MarkerGhost.draw(tickDelta);
    }
}
*///?}
