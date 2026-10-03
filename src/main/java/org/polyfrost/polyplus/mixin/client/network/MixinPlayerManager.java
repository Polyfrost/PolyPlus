package org.polyfrost.polyplus.mixin.client.network;

//? if = 1.8.9 {
/*import net.minecraft.network.Connection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import org.polyfrost.polyplus.client.resourcepack.HostSharedPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class MixinPlayerManager {
    @Inject(method = "onLogin", at = @At("TAIL"))
    private void polyplus$offerSharedPack(Connection connection, ServerPlayerEntity player, CallbackInfo ci) {
        HostSharedPack.offerTo(connection, player);
    }
}
*///?}
