package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PipelineCache.class)
public interface NameplatePipelineCacheMapAccessor {
    @Accessor("cache")
    Map<RenderPipeline, CompiledRenderPipeline> polyplus$cache();
}
//?}
