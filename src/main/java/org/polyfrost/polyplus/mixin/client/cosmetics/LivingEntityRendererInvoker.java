package org.polyfrost.polyplus.mixin.client.cosmetics;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
    @SuppressWarnings("UnusedReturnValue")
    @Invoker("addLayer")
    boolean polyplus$invokeAddLayer(RenderLayer<?, ?> layer);

    @Accessor("layers")
    List<RenderLayer<?, ?>> polyplus$layers();
}
