package org.polyfrost.polyplus.mixin.compat.iris;

import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.polyfrost.polyplus.compat.iris.IrisLobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public abstract class MixinIrisPipelineManager {
    @ModifyVariable(method = "preparePipeline(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;", at = @At("HEAD"), argsOnly = true, remap = false, require = 0, expect = 0)
    private NamespacedId polyplus$routeLobby(NamespacedId dimension) {
        if (!IrisLobbyPipelines.armed) {
            return dimension;
        }
        return IrisLobbyPipelines.route(this, dimension);
    }
}
