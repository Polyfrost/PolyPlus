package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(IntegratedServer.class)
public class MixinIntegratedServer {
    //? if >= 26.2 {
    @WrapMethod(method = "updatePermissionAndChatAbilities")
    private void polyplus$deferToRenderThread(LocalPlayer player, Operation<Void> original) {
        if (RenderSystem.isOnRenderThread()) {
            original.call(player);
        } else {
            Minecraft.getInstance().execute(() -> original.call(player));
        }
    }
    //?}
}
