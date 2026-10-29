package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderTarget.class)
public class MixinRenderTarget {
}
//?} else {
/*import net.minecraft.client.render.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderTarget.class)
public class MixinRenderTarget {
}
*///?}
