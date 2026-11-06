package org.polyfrost.polyplus.mixin.client.access;

//? if >= 1.21.5 {
//? if >= 26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("ENTITY_SNIPPET")
    static RenderPipeline.Snippet polyplus$entitySnippet() {
        throw new AssertionError();
    }

    @Accessor("TERRAIN_SNIPPET")
    static RenderPipeline.Snippet polyplus$terrainSnippet() {
        throw new AssertionError();
    }

    //? if >= 26.3 {
    @Accessor("MULTIDRAW_TERRAIN_SNIPPET")
    static RenderPipeline.Snippet polyplus$multidrawTerrainSnippet() {
        throw new AssertionError();
    }
    //?}
}
//?}
