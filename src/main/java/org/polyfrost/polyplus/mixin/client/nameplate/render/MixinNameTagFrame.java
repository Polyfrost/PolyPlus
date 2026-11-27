package org.polyfrost.polyplus.mixin.client.nameplate.render;

//? if >= 1.21.10 || = 1.8.9 {
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >= 1.21.10 {
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.polyfrost.polyplus.client.nameplate.render.NameTagCanvas;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import org.polyfrost.polyplus.client.nameplate.render.LegacyNameTagDeferral;
import org.polyfrost.polyplus.client.nameplate.render.NameTags;
*///?}
//? if >= 26.1 {
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} elif >= 1.21.10 {
/*import net.minecraft.client.renderer.state.LevelRenderState;
*///?}
//? if < 26.2 && >= 1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}

//? if >= 1.21.10 {
@Mixin(LevelRenderer.class)
//?} else {
/*@Mixin(GameRenderer.class)
*///?}
public class MixinNameTagFrame {
    //? if >= 26.2 {
    @Inject(
        method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
        at = @At("HEAD")
    )
    private void polyplus$beginNameTags(LevelRenderState levelState, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
        NameTagCanvas.begin(collector);
    }

    @Inject(
        method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
        at = @At("TAIL")
    )
    private void polyplus$flushNameTags(LevelRenderState levelState, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
        NameTagCanvas.flush();
    }
    //?} elif >= 1.21.10 {
    /*@Inject(method = "submitEntities", at = @At("HEAD"))
    private void polyplus$beginNameTags(PoseStack poseStack, LevelRenderState levelState, SubmitNodeCollector collector, CallbackInfo ci) {
        NameTagCanvas.begin(collector);
    }

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void polyplus$flushNameTags(PoseStack poseStack, LevelRenderState levelState, SubmitNodeCollector collector, CallbackInfo ci) {
        NameTagCanvas.flush();
    }
    *///?} else {
    /*@Inject(
        method = "render(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Culler;F)V")
    )
    private void polyplus$collectNameTags(int anaglyphPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        LegacyNameTagDeferral.beginEntities();
    }

    @Inject(
        method = "render(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/world/WorldRenderer;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Culler;F)V",
            shift = At.Shift.AFTER
        )
    )
    private void polyplus$stopCollectingNameTags(int anaglyphPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        LegacyNameTagDeferral.endEntities();
    }

    @WrapOperation(
        method = "render(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I")
    )
    private int polyplus$drawNameTagsAfterTranslucent(
        WorldRenderer worldRenderer,
        BlockLayer layer,
        double tickDelta,
        int anaglyphPass,
        Entity camera,
        Operation<Integer> original
    ) {
        int drawn = original.call(worldRenderer, layer, tickDelta, anaglyphPass, camera);
        if (layer == BlockLayer.TRANSLUCENT) {
            LegacyNameTagDeferral.flush((float) tickDelta);
        }
        return drawn;
    }

    @Inject(method = "render(IFJ)V", at = @At("TAIL"))
    private void polyplus$endNameTagFrame(int anaglyphPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        LegacyNameTagDeferral.endFrame();
        NameTags.endFrame();
    }
    *///?}
}
//?}
