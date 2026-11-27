package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.5 && < 26.3 {
/*import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public interface NameplateGlPipelinesAccessor {
    @Accessor("pipelineCache")
    Map<RenderPipeline, GlRenderPipeline> polyplus$pipelines();
}
*///?}
