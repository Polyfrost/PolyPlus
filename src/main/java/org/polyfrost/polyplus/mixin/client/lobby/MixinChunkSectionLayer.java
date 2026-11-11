package org.polyfrost.polyplus.mixin.client.lobby;

//? if >= 1.21.8 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//? if >= 26.3 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
*///?}
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.polyfrost.polyplus.client.lobby.LobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSectionLayer.class)
public class MixinChunkSectionLayer {
    @ModifyReturnValue(method = "pipeline", at = @At("RETURN"))
    //? if >= 26.3 {
    private RenderPipeline polyplus$lobbyTerrain(RenderPipeline original, boolean multiDraw) {
        return LobbyPipelines.select((ChunkSectionLayer) (Object) this, multiDraw, original);
    }
    //?} else {
    /*private RenderPipeline polyplus$lobbyTerrain(RenderPipeline original) {
        return LobbyPipelines.select((ChunkSectionLayer) (Object) this, false, original);
    }
    *///?}
}
//?}
