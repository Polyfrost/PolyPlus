package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSystem.class)
public interface NameplatePipelineCacheAccessor {
    @Accessor("currentPipelineCache")
    static PipelineCache polyplus$current() {
        throw new AssertionError();
    }

    @Accessor("fallbackPipelineCache")
    static PipelineCache polyplus$fallback() {
        throw new AssertionError();
    }
}
//?}
