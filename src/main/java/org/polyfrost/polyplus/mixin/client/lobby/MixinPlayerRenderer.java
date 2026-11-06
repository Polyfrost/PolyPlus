package org.polyfrost.polyplus.mixin.client.lobby;

//? if = 1.8.9 {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import org.polyfrost.polyplus.client.lobby.LobbyAvatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class MixinPlayerRenderer {
    @Inject(method = "applyScale", at = @At("HEAD"))
    private void polyplus$fadeLobbyAvatar(ClientPlayerEntity entity, float scale, CallbackInfo ci) {
        if (entity instanceof LobbyAvatar) {
            float size = ((LobbyAvatar) entity).getScale();
            GlStateManager.scalef(size, size, size);
        }
    }
}
*///?}
