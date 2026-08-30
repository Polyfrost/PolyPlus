package org.polyfrost.polyplus.mixin.client.cosmetics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
    @SuppressWarnings("UnusedReturnValue")
    @Invoker("addLayer")
    boolean polyplus$invokeAddLayer(RenderLayer<?, ?> layer);
}
