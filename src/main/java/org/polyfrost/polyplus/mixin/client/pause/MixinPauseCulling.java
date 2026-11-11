package org.polyfrost.polyplus.mixin.client.pause;

import org.polyfrost.polyplus.client.gui.pausescreen.PauseStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if > 1.8.9 {
import net.minecraft.client.renderer.SectionOcclusionGraph;
//?} else {
/*import net.minecraft.client.render.world.WorldRenderer;
*///?}

//? if > 1.8.9 {
@Mixin(SectionOcclusionGraph.class)
public abstract class MixinPauseCulling {
    @Shadow
    public abstract void invalidate();

    @Inject(method = "update", at = @At("HEAD"))
    private void polyplus$regraphStagedCamera(CallbackInfo ci) {
        if (PauseStage.consumeRegraph()) this.invalidate();
    }
}
//?} else {
/*@Mixin(value = WorldRenderer.class, priority = 1100)
public abstract class MixinPauseCulling {
    @Shadow
    private boolean viewChanged;

    @Inject(method = "setupRender", at = @At("HEAD"))
    private void polyplus$recullStagedCamera(CallbackInfo ci) {
        if (PauseStage.consumeCullMove()) this.viewChanged = true;
    }
}
*///?}
