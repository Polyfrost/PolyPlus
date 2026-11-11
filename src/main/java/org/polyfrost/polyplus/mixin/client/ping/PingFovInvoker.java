package org.polyfrost.polyplus.mixin.client.ping;

//? if = 1.8.9 {
/*import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface PingFovInvoker {
    @Invoker("getFov")
    float polyplus$pingFov(float tickDelta, boolean fovChanged);
}
*///?}
