package org.polyfrost.polyplus.mixin.compat.iris;

//? if >= 26.2 {
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.uniforms.IrisTimeUniforms;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >= 26.3 {
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
//?}

@Mixin(value = LevelRenderer.class, priority = 1500)
public class MixinLevelRenderer {
    private static final String IRIS_MIXIN = "net.irisshaders.iris.mixin.MixinLevelRenderer";
    private static final String IRIS_SKY_MIXIN = "net.irisshaders.iris.mixin.MixinLevelRenderer_Sky";

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$setupPipeline")
    @WrapWithCondition(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/uniforms/IrisTimeUniforms;updateTime()V"), require = 0, expect = 0)
    private boolean polyplus$deferUpdateTime() {
        return false;
    }

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$setupPipeline")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pipeline/PipelineManager;preparePipeline(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;"), require = 0, expect = 0)
    private WorldRenderingPipeline polyplus$updateTimeWithPack(PipelineManager manager, NamespacedId dimension, Operation<WorldRenderingPipeline> original) {
        WorldRenderingPipeline pipeline = original.call(manager, dimension);
        if (pipeline instanceof IrisRenderingPipeline) IrisTimeUniforms.updateTime();
        return pipeline;
    }

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$beginLevelRender")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void polyplus$skipEmptySetupPass(CallbackInfo ci) {
        if (!Iris.isPackInUseQuick()) ci.cancel();
    }

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$endLevelRender")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"), require = 0, expect = 0)
    private void polyplus$pushIrisFinal(ProfilerFiller profiler, String name, Operation<Void> original) {
        if (Iris.isPackInUseQuick()) profiler.push(name);
    }

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$endLevelRender")
    @Inject(method = "@MixinSquared:Handler", at = @At("TAIL"), require = 0, expect = 0)
    private void polyplus$popIrisFinal(CallbackInfo ci) {
        if (Iris.isPackInUseQuick()) Profiler.get().pop();
    }

    //? if >= 26.3 {
    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$beginTranslucents")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void polyplus$skipTranslucentSetup(LevelRenderer instance, ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame, RenderPass renderPass, Operation<Void> original, CallbackInfo ci) {
        if (!Iris.isPackInUseQuick()) {
            original.call(instance, sections, frame, renderPass);
            ci.cancel();
        }
    }
    //?} else {
    /*@TargetHandler(mixin = IRIS_MIXIN, name = "iris$beginTranslucents")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void polyplus$skipTranslucentSetup(CallbackInfo ci) {
        if (!Iris.isPackInUseQuick()) ci.cancel();
    }
    *///?}

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$beginTranslucents")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"), require = 0, expect = 0)
    private void polyplus$pushPreTranslucent(ProfilerFiller profiler, String name, Operation<Void> original) {
        profiler.push(name);
    }

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$beginTranslucents")
    @Inject(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;beginTranslucents()V", shift = At.Shift.AFTER), require = 0, expect = 0)
    private void polyplus$popPreTranslucent(CallbackInfo ci) {
        Profiler.get().pop();
    }

    @TargetHandler(mixin = IRIS_SKY_MIXIN, name = "preRenderSky")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"), require = 0, expect = 0)
    private static FogType polyplus$extractedFogType(Camera camera, Operation<FogType> original) {
        return Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogType;
    }

    @TargetHandler(mixin = IRIS_SKY_MIXIN, name = "preRenderSky")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;mainCamera()Lnet/minecraft/client/Camera;"), require = 0, expect = 0)
    private static Camera polyplus$skipMainCamera(GameRenderer gameRenderer, Operation<Camera> original) {
        return null;
    }

    @TargetHandler(mixin = IRIS_SKY_MIXIN, name = "preRenderSky")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;position()Lnet/minecraft/world/phys/Vec3;"), require = 0, expect = 0)
    private static Vec3 polyplus$skipCameraPosition(Camera camera, Operation<Vec3> original) {
        return null;
    }

    @TargetHandler(mixin = IRIS_SKY_MIXIN, name = "preRenderSky")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;entity()Lnet/minecraft/world/entity/Entity;"), require = 0, expect = 0)
    private static Entity polyplus$skipCameraEntity(Camera camera, Operation<Entity> original) {
        return null;
    }
}
//?}
