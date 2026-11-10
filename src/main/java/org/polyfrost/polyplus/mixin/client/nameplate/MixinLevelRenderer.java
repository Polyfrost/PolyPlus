package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.2 {
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.polyfrost.polyplus.client.nameplate.render.NameplateOrdering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} elif >= 26.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.polyfrost.polyplus.client.nameplate.render.NameplateOrdering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif >= 1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.polyfrost.polyplus.client.nameplate.render.NameplateOrdering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

//? if >= 1.21.10 {
@Mixin(LevelRenderer.class)
//?}
public class MixinLevelRenderer {
    //? if >= 26.2 {
    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void polyplus$beginNameplateOrdering(LevelRenderState levelRenderState, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
        NameplateOrdering.begin(collector);
    }

    @Inject(method = "submitFeatures", at = @At("TAIL"))
    private void polyplus$flushNameplateOrdering(LevelRenderState levelRenderState, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
        NameplateOrdering.flush();
    }
    //?} elif >= 1.21.10 {
    /*@Inject(method = "submitEntities", at = @At("HEAD"))
    private void polyplus$beginNameplateOrdering(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector collector, CallbackInfo ci) {
        NameplateOrdering.begin(collector);
    }

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void polyplus$flushNameplateOrdering(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector collector, CallbackInfo ci) {
        NameplateOrdering.flush();
    }
    *///?}
}
