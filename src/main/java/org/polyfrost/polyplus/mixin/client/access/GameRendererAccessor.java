package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
//? if < 26.3 && >= 1.21.4
/*import com.mojang.blaze3d.resource.CrossFrameResourcePool;*/
//? if >= 26.1
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
//? if >= 26.1
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//? if >= 26.1
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    //? if >= 26.1 {
    @Accessor("lightmapRenderStateExtractor")
    LightmapRenderStateExtractor polyplus$lightmapExtractor();
    //?}

    //? if >= 26.3 {
    @Invoker("extractCamera")
    void polyplus$extractCamera(DeltaTracker deltaTracker, float worldPartialTicks);
    //?} elif >= 26.1 {
    /*@Invoker("extractCamera")
    void polyplus$extractCamera(DeltaTracker deltaTracker, float worldPartialTicks, float cameraEntityPartialTicks);
    *///?}
    //? if < 26.3 && >= 1.21.4 {
    /*@Accessor("resourcePool")
    CrossFrameResourcePool polyplus$resourcePool();
    *///?}
}
//?} else {
/*import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("renderBlockOutline")
    void polyplus$setRenderBlockOutline(boolean show);
}
*///?}
