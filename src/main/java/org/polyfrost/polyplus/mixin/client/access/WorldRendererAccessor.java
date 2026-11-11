package org.polyfrost.polyplus.mixin.client.access;

//? if = 1.8.9 {
/*import java.util.Set;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {
    @Accessor("dirtyChunks")
    Set<RenderChunk> polyplus$dirtyChunks();
}
*///?}
