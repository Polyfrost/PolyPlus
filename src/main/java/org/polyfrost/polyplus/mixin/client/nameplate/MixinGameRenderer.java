package org.polyfrost.polyplus.mixin.client.nameplate;

//? if = 1.21.1 {
/*import net.minecraft.client.renderer.GameRenderer;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyMatrixStack;
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateDeferral;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

//? if = 1.8.9 || = 1.21.1 {
/*@Mixin(GameRenderer.class)
*///?}
public class MixinGameRenderer {
    //? if = 1.21.1 {
    /*@Inject(method = "reloadShaders", at = @At("TAIL"))
    private void polyplus$compileNameplateMaterials(CallbackInfo ci) {
        NameplateMaterialPipelines.onShadersApplied();
    }
    *///?} elif = 1.8.9 {
    /*@Inject(
        method = "render(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Culler;F)V")
    )
    private void polyplus$collectNameplates(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        LegacyNameplateDeferral.beginEntities();
    }

    @Inject(
        method = "render(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Culler;F)V", shift = At.Shift.AFTER)
    )
    private void polyplus$stopCollectingNameplates(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        LegacyNameplateDeferral.endEntities();
    }

    @WrapOperation(
        method = "render(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I")
    )
    private int polyplus$drawNameplatesAfterTranslucent(WorldRenderer worldRenderer, BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera, Operation<Integer> original) {
        int result = original.call(worldRenderer, layer, tickDelta, anaglyphRenderPass, camera);
        if (layer == BlockLayer.TRANSLUCENT) {
            LegacyNameplateDeferral.flush((float) tickDelta);
        }
        return result;
    }

    @Inject(method = "render(IFJ)V", at = @At("TAIL"))
    private void polyplus$checkNameplatesDrawn(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        LegacyNameplateDeferral.endFrame();
    }

    @WrapOperation(
        method = "render(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;render(F)V")
    )
    private void polyplus$keepHudMatrixStack(GameGui gui, float tickDelta, Operation<Void> original) {
        int depth = LegacyMatrixStack.depth();
        try {
            original.call(gui, tickDelta);
        } finally {
            LegacyMatrixStack.restore(depth);
        }
    }
    *///?}
}
