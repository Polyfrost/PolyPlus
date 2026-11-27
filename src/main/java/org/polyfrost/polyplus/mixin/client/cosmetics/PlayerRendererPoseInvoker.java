package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerRenderer.class)
public interface PlayerRendererPoseInvoker {
    @Invoker("setModelProperties")
    void polyplus$setModelProperties(AbstractClientPlayer player);
}
*///?} elif = 1.8.9 {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerRenderer.class)
public interface PlayerRendererPoseInvoker {
    @Invoker("setModelStatus")
    void polyplus$setModelStatus(ClientPlayerEntity player);
}
*///?}
